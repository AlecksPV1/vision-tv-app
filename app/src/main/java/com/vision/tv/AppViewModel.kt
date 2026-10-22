package com.vision.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.vision.tv.data.AccountInfo
import com.vision.tv.data.Channel
import com.vision.tv.data.ChannelsState
import com.vision.tv.data.CloudRepo
import com.vision.tv.data.Profile
import com.vision.tv.data.Source
import com.vision.tv.data.SourceType
import com.vision.tv.data.XtreamClient
import com.vision.tv.data.loadM3u
import com.vision.tv.data.normalizeBase
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

const val CAT_FAVS = "Favoritos"
const val CAT_RECENTS = "Recientes"
const val CAT_ALL = "Todos"

sealed interface Screen {
    data object Login : Screen
    data object Profiles : Screen
    data class ProfileEditor(val profile: Profile?) : Screen
    data object Sources : Screen
    data class SourceEditor(val source: Source?) : Screen
    data object Channels : Screen
    data class Player(val index: Int) : Screen
}

fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val uid = MutableStateFlow(auth.currentUser?.uid)
    private val repoFlow: Flow<CloudRepo?> = uid.map { id -> id?.let { CloudRepo(it) } }
    private fun repo(): CloudRepo? = uid.value?.let { CloudRepo(it) }

    // ---- Navegación ----
    val stack = MutableStateFlow<List<Screen>>(
        listOf(if (auth.currentUser != null) Screen.Profiles else Screen.Login)
    )

    fun push(s: Screen) = stack.update { it + s }
    fun pop() = stack.update { if (it.size > 1) it.dropLast(1) else it }
    fun reset(s: Screen) {
        stack.value = listOf(s)
    }

    // ---- Estado compartido ----
    val activeProfile = MutableStateFlow<Profile?>(null)
    val activeSource = MutableStateFlow<Source?>(null)
    val channels = MutableStateFlow(ChannelsState())
    val category = MutableStateFlow(CAT_ALL)
    val query = MutableStateFlow("")
    val gridState = LazyGridState()
    val catState = LazyListState()

    var queue: List<Channel> = emptyList()
        private set

    // null = todavía cargando
    val profiles: StateFlow<List<Profile>?> = repoFlow
        .flatMapLatest { r -> if (r == null) flowOf<List<Profile>?>(null) else r.profiles() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sources: StateFlow<List<Source>?> = repoFlow
        .flatMapLatest { r -> if (r == null) flowOf<List<Source>?>(null) else r.sources() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val favorites: StateFlow<List<Channel>> = combine(repoFlow, activeProfile) { r, p -> r to p }
        .flatMapLatest { (r, p) ->
            if (r != null && p != null) r.favorites(p.id) else flowOf(emptyList<Channel>())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recents: StateFlow<List<Channel>> = combine(repoFlow, activeProfile) { r, p -> r to p }
        .flatMapLatest { (r, p) ->
            if (r != null && p != null) r.recents(p.id) else flowOf(emptyList<Channel>())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Cuenta ----
    fun signIn(email: String, pass: String, create: Boolean, onError: (String) -> Unit) {
        if (email.isBlank() || pass.isEmpty()) {
            onError("Escribe tu correo y contraseña.")
            return
        }
        viewModelScope.launch {
            try {
                val res = if (create) {
                    auth.createUserWithEmailAndPassword(email.trim(), pass).await()
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), pass).await()
                }
                uid.value = res.user?.uid
                reset(Screen.Profiles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(friendly(e))
            }
        }
    }

    fun signOut() {
        auth.signOut()
        uid.value = null
        activeProfile.value = null
        activeSource.value = null
        channels.value = ChannelsState()
        reset(Screen.Login)
    }

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil (mínimo 6 caracteres)."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseNetworkException -> "Sin conexión a internet."
        else -> e.message ?: "Error desconocido."
    }

    // ---- Perfiles ----
    fun saveProfile(p: Profile) {
        repo()?.saveProfile(p)
        if (activeProfile.value?.id == p.id) activeProfile.value = p
    }

    fun deleteProfile(id: String) {
        repo()?.deleteProfile(id)
        if (activeProfile.value?.id == id) activeProfile.value = null
        pop()
    }

    fun chooseProfile(p: Profile) {
        activeProfile.value = p
        viewModelScope.launch {
            val list = sources.filterNotNull().first()
            when {
                list.isEmpty() -> push(Screen.SourceEditor(null))
                list.size == 1 -> openSource(list[0])
                else -> push(Screen.Sources)
            }
        }
    }

    fun switchProfile() {
        activeProfile.value = null
        reset(Screen.Profiles)
    }

    // ---- Fuentes y canales ----
    fun openSource(s: Source) {
        activeSource.value = s
        category.value = CAT_ALL
        query.value = ""
        stack.update { st ->
            st.dropLastWhile {
                it is Screen.Sources || it is Screen.SourceEditor || it is Screen.Channels
            } + Screen.Channels
        }
        loadChannels()
    }

    private var loadJob: Job? = null

    fun loadChannels() {
        val s = activeSource.value ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            channels.value = ChannelsState(loading = true)
            try {
                if (s.type == SourceType.XTREAM) {
                    val client = XtreamClient(s.url, s.username, s.password)
                    val acc: AccountInfo = client.account()
                    channels.value = ChannelsState(loading = true, account = acc)
                    val list = client.liveChannels(s.id)
                    channels.value = ChannelsState(channels = list, account = acc)
                } else {
                    channels.value = ChannelsState(channels = loadM3u(s.id, s.url))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                channels.value = ChannelsState(error = e.message ?: "No se pudieron cargar los canales")
            }
        }
    }

    /** Valida (Xtream), guarda en la nube y abre la fuente si es la primera. */
    fun saveSource(input: Source, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val s = if (input.type == SourceType.XTREAM) {
                    input.copy(url = normalizeBase(input.url))
                } else {
                    input.copy(url = input.url.trim())
                }
                if (s.type == SourceType.XTREAM) {
                    XtreamClient(s.url, s.username, s.password).account()
                }
                val r = repo() ?: error("Sesión cerrada")
                r.saveSource(s)
                if (activeSource.value?.id == s.id) activeSource.value = s
                val openIt = activeProfile.value != null && activeSource.value == null
                if (openIt) openSource(s) else pop()
                onDone(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onDone(e.message ?: "No se pudo conectar con la fuente")
            }
        }
    }

    fun deleteSource(id: String) {
        repo()?.deleteSource(id)
        if (activeSource.value?.id == id) {
            activeSource.value = null
            channels.value = ChannelsState()
        }
        pop()
    }

    fun setCategory(c: String) {
        category.value = c
        viewModelScope.launch { gridState.scrollToItem(0) }
    }

    fun setQuery(q: String) {
        query.value = q
        viewModelScope.launch { gridState.scrollToItem(0) }
    }

    // ---- Reproducción, favoritos y recientes ----
    fun play(list: List<Channel>, index: Int) {
        if (list.isEmpty()) return
        queue = list
        push(Screen.Player(index))
    }

    fun toggleFavorite(c: Channel) {
        val r = repo() ?: return
        val p = activeProfile.value ?: return
        if (favorites.value.any { it.key == c.key }) r.removeFavorite(p.id, c.key) else r.addFavorite(p.id, c)
    }

    fun addRecent(c: Channel) {
        val p = activeProfile.value ?: return
        repo()?.addRecent(p.id, c)
    }
}
