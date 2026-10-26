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
import com.vision.tv.data.CatalogState
import com.vision.tv.data.CloudRepo
import com.vision.tv.data.DetailState
import com.vision.tv.data.Media
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Profile
import com.vision.tv.data.Progress
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

const val CAT_FAVS = "Favoritos"
const val CAT_RECENTS = "Recientes"
const val CAT_ALL = "Todos"
const val CAT_CONTINUE = "Continuar viendo"
const val CAT_WATCHLIST = "Mi lista"

sealed interface Screen {
    data object Login : Screen
    data object Profiles : Screen
    data class ProfileEditor(val profile: Profile?) : Screen
    data object Sources : Screen
    data class SourceEditor(val source: Source?) : Screen
    data object Home : Screen
    data object MyList : Screen
    data class Catalog(val kind: MediaKind) : Screen
    data class Detail(val media: Media) : Screen
    data class Player(val index: Int) : Screen
}

fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/** Estado de una sección (TV en vivo, películas o series): catálogo, categoría, búsqueda y scroll. */
class BrowseState(val kind: MediaKind) {
    val catalog = MutableStateFlow(CatalogState())
    /** Vacío = filas por categoría; si no, la categoría abierta como cuadrícula. */
    val category = MutableStateFlow("")
    val query = MutableStateFlow("")
    val gridState = LazyGridState()
    val catState = LazyListState()
    var job: Job? = null

    fun reset() {
        job?.cancel()
        catalog.value = CatalogState()
        resetView()
    }

    /** Deja la sección como recién abierta (sin categoría ni búsqueda), sin volver a descargar nada. */
    fun resetView() {
        category.value = ""
        query.value = ""
    }
}

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

    fun pop() {
        stack.update { if (it.size > 1) it.dropLast(1) else it }
        // Al volver a "¿Quién está viendo?" (por ejemplo con el botón Atrás) se suelta el perfil
        // activo: nada de lo suyo debe verse cuando entre otra persona.
        if (stack.value.last() == Screen.Profiles) clearProfile()
    }

    fun reset(s: Screen) {
        stack.value = listOf(s)
    }

    /** Cambia de pestaña (Inicio, TV en vivo, Películas, Series, Mi lista) sin apilar pantallas. */
    fun switchTab(s: Screen) = stack.update { st ->
        val base = st.takeWhile {
            it !is Screen.Home && it !is Screen.Catalog && it !is Screen.MyList &&
                it !is Screen.Detail && it !is Screen.Player
        }
        if (s is Screen.Home) base + Screen.Home else base + Screen.Home + s
    }

    // ---- Estado compartido ----
    val activeProfile = MutableStateFlow<Profile?>(null)
    val activeSource = MutableStateFlow<Source?>(null)
    val account = MutableStateFlow<AccountInfo?>(null)
    val detail = MutableStateFlow(DetailState())
    val browse: Map<MediaKind, BrowseState> =
        listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES).associateWith { BrowseState(it) }

    var queue: List<Media> = emptyList()
        private set
    var fromStart: Boolean = false
        private set

    // null = todavía cargando
    val profiles: StateFlow<List<Profile>?> = repoFlow
        .flatMapLatest { r -> if (r == null) flowOf<List<Profile>?>(null) else r.profiles() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sources: StateFlow<List<Source>?> = repoFlow
        .flatMapLatest { r -> if (r == null) flowOf<List<Source>?>(null) else r.sources() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Datos que cuelgan del perfil activo (favoritos, lista, progreso…). */
    private fun <T> scoped(empty: T, source: (CloudRepo, String) -> Flow<T>): StateFlow<T> =
        combine(repoFlow, activeProfile) { r, p -> r to p }
            .flatMapLatest { (r, p) ->
                // onStart vacía el estado en el mismo instante en que cambia el perfil, antes de
                // que Firestore entregue los datos del nuevo: así nunca se ven los del anterior.
                if (r != null && p != null) source(r, p.id).onStart { emit(empty) } else flowOf(empty)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, empty)

    private fun forSource(list: List<Media>, s: Source?): List<Media> =
        if (s == null) list else list.filter { it.key.startsWith(s.id + "_") }

    private fun scopedMedia(source: (CloudRepo, String) -> Flow<List<Media>>): StateFlow<List<Media>> {
        val raw = scoped(emptyList<Media>(), source)
        return combine(raw, activeSource) { l, s -> forSource(l, s) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    val favorites: StateFlow<List<Media>> = scopedMedia { r, pid -> r.favorites(pid) }
    val watchlist: StateFlow<List<Media>> = scopedMedia { r, pid -> r.watchlist(pid) }
    val recents: StateFlow<List<Media>> = scopedMedia { r, pid -> r.recents(pid) }

    private val progressRaw: StateFlow<List<Progress>> = scoped(emptyList<Progress>()) { r, pid -> r.progress(pid) }

    val progress: StateFlow<List<Progress>> = combine(progressRaw, activeSource) { l, s ->
        if (s == null) l else l.filter { it.media.key.startsWith(s.id + "_") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progressMap: StateFlow<Map<String, Progress>> = progress
        .map { l -> l.associateBy { it.media.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * "Continuar viendo": películas a medias y, de cada serie, el último episodio visto.
     * Ya viene ordenado del más reciente al más antiguo.
     */
    val continueItems: StateFlow<List<Progress>> = progress.map { list ->
        val seenSeries = HashSet<String>()
        list.filter { p ->
            when (p.media.kind) {
                MediaKind.MOVIE -> !p.finished
                MediaKind.EPISODE -> seenSeries.add(p.media.seriesId)
                else -> false
            }
        }.take(12)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        clearProfile()
        activeSource.value = null
        account.value = null
        browse.values.forEach { it.reset() }
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
        if (activeProfile.value?.id == id) clearProfile()
        pop()
    }

    /** Suelta todo lo que pertenece al perfil que estaba activo (estado, búsquedas, ficha, cola). */
    private fun clearProfile() {
        activeProfile.value = null
        queue = emptyList()
        fromStart = false
        detail.value = DetailState()
        browse.values.forEach { it.resetView() }
    }

    fun chooseProfile(p: Profile) {
        clearProfile()
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
        clearProfile()
        reset(Screen.Profiles)
    }

    // ---- Fuentes y catálogos ----
    fun openSource(s: Source) {
        val same = activeSource.value == s
        activeSource.value = s
        if (same) {
            // Misma fuente (por ejemplo al entrar otro perfil): se conserva lo ya descargado.
            browse.values.forEach { it.resetView() }
        } else {
            browse.values.forEach { it.reset() }
        }
        viewModelScope.launch {
            browse.values.forEach { b ->
                b.gridState.scrollToItem(0)
                b.catState.scrollToItem(0)
            }
        }
        loadAccount()
        stack.update { st ->
            st.dropLastWhile {
                it is Screen.Sources || it is Screen.SourceEditor || it is Screen.Home ||
                    it is Screen.MyList || it is Screen.Catalog || it is Screen.Detail
            } + Screen.Home
        }
    }

    /**
     * Descarga en segundo plano películas y luego series para las filas del Inicio
     * (de una en una, para no saturar la memoria de un Fire Stick).
     */
    fun preloadShelves() {
        val s = activeSource.value ?: return
        if (s.type != SourceType.XTREAM) return
        viewModelScope.launch {
            loadCatalog(MediaKind.MOVIE)
            browse.getValue(MediaKind.MOVIE).job?.join()
            loadCatalog(MediaKind.SERIES)
        }
    }

    private var accountJob: Job? = null

    private fun loadAccount() {
        val s = activeSource.value ?: return
        account.value = null
        if (s.type != SourceType.XTREAM) return
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            try {
                account.value = XtreamClient(s.url, s.username, s.password).account()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // La cuenta es informativa: si falla, el resto de la app sigue funcionando.
            }
        }
    }

    /** Descarga el catálogo de una sección la primera vez que se abre (o al actualizar). */
    fun loadCatalog(kind: MediaKind, force: Boolean = false) {
        val s = activeSource.value ?: return
        val b = browse.getValue(kind)
        val cur = b.catalog.value
        if (!force && (cur.loading || cur.items.isNotEmpty())) return
        b.job?.cancel()
        b.job = viewModelScope.launch {
            b.catalog.value = CatalogState(loading = true)
            try {
                val items: List<Media> = if (s.type == SourceType.XTREAM) {
                    val c = XtreamClient(s.url, s.username, s.password)
                    when (kind) {
                        MediaKind.LIVE -> c.liveChannels(s.id)
                        MediaKind.MOVIE -> c.movies(s.id)
                        MediaKind.SERIES -> c.seriesList(s.id)
                        MediaKind.EPISODE -> emptyList()
                    }
                } else if (kind == MediaKind.LIVE) {
                    loadM3u(s.id, s.url)
                } else {
                    emptyList()
                }
                b.catalog.value = CatalogState(items = items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                b.catalog.value = CatalogState(error = e.message ?: "No se pudo cargar el catálogo")
            }
        }
        if (kind == MediaKind.LIVE) loadAccount()
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
            account.value = null
            browse.values.forEach { it.reset() }
        }
        pop()
    }

    fun setCategory(kind: MediaKind, c: String) {
        val b = browse.getValue(kind)
        b.category.value = c
        viewModelScope.launch { b.gridState.scrollToItem(0) }
    }

    fun setQuery(kind: MediaKind, q: String) {
        val b = browse.getValue(kind)
        b.query.value = q
        viewModelScope.launch { b.gridState.scrollToItem(0) }
    }

    // ---- Fichas (película / serie) ----
    private var detailJob: Job? = null

    fun openDetail(m: Media) {
        push(Screen.Detail(m))
        loadDetail(m)
    }

    fun loadDetail(m: Media) {
        detail.value = DetailState(loading = true)
        val s = activeSource.value ?: return
        if (s.type != SourceType.XTREAM) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            try {
                val c = XtreamClient(s.url, s.username, s.password)
                detail.value = if (m.kind == MediaKind.SERIES) c.seriesInfo(s.id, m) else c.vodInfo(m)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                detail.value = DetailState(error = e.message ?: "No se pudo cargar la ficha")
            }
        }
    }

    /** Reconstruye la serie a partir de uno de sus episodios (para abrirla desde "Continuar viendo"). */
    fun seriesOf(ep: Media): Media = Media(
        key = ep.key.substringBefore("_e") + "_s" + ep.seriesId,
        name = ep.seriesName,
        logo = ep.logo,
        group = "",
        streamUrl = "",
        kind = MediaKind.SERIES,
        itemId = ep.seriesId,
    )

    /**
     * Qué episodio toca ver: el que quedó a medias, el siguiente al último terminado, o el primero.
     * Devuelve (posición en la lista, true si hay un avance que reanudar).
     */
    fun episodeTarget(eps: List<Media>): Pair<Int, Boolean> {
        if (eps.isEmpty()) return 0 to false
        val map = progressMap.value
        var lastIdx = -1
        var lastAt = 0L
        eps.forEachIndexed { i, e ->
            val p = map[e.key]
            if (p != null && p.updatedAt >= lastAt) {
                lastAt = p.updatedAt
                lastIdx = i
            }
        }
        if (lastIdx < 0) return 0 to false
        val p = map.getValue(eps[lastIdx].key)
        return when {
            !p.finished -> lastIdx to (p.positionMs >= 10_000)
            lastIdx + 1 < eps.size -> (lastIdx + 1) to false
            else -> lastIdx to false
        }
    }

    // ---- Reproducción, progreso, favoritos y lista ----
    fun play(list: List<Media>, index: Int, fromStart: Boolean = false) {
        if (list.isEmpty()) return
        queue = list
        this.fromStart = fromStart
        push(Screen.Player(index))
    }

    fun playMovie(m: Media, fromStart: Boolean = false) = play(listOf(m), 0, fromStart)

    /** Milisegundos desde donde reanudar (0 si no hay avance o ya se terminó). */
    fun resumePosition(m: Media): Long {
        val p = progressMap.value[m.key] ?: return 0L
        return if (p.finished || p.positionMs < 10_000) 0L else (p.positionMs - 3_000).coerceAtLeast(0L)
    }

    fun saveProgress(m: Media, positionMs: Long, durationMs: Long) {
        if (m.kind != MediaKind.MOVIE && m.kind != MediaKind.EPISODE) return
        val p = activeProfile.value ?: return
        repo()?.saveProgress(p.id, Progress(m, positionMs, durationMs, System.currentTimeMillis()))
    }

    fun toggleFavorite(m: Media) {
        val r = repo() ?: return
        val p = activeProfile.value ?: return
        if (favorites.value.any { it.key == m.key }) r.removeFavorite(p.id, m.key) else r.addFavorite(p.id, m)
    }

    fun toggleWatchlist(m: Media) {
        val r = repo() ?: return
        val p = activeProfile.value ?: return
        if (watchlist.value.any { it.key == m.key }) r.removeWatchlist(p.id, m.key) else r.addWatchlist(p.id, m)
    }

    fun addRecent(m: Media) {
        if (m.kind != MediaKind.LIVE) return
        val p = activeProfile.value ?: return
        repo()?.addRecent(p.id, m)
    }
}
