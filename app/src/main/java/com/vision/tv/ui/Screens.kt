package com.vision.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.tv.AppViewModel
import com.vision.tv.Screen
import com.vision.tv.data.Profile
import com.vision.tv.data.Source
import com.vision.tv.data.SourceType
import com.vision.tv.sha256
import java.util.UUID

@Composable
fun AppRoot(vm: AppViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    BackHandler(enabled = stack.size > 1) { vm.pop() }
    VisionBackground {
        when (val s = stack.last()) {
            Screen.Login -> LoginScreen(vm)
            Screen.Profiles -> ProfilesScreen(vm)
            is Screen.ProfileEditor -> ProfileEditorScreen(vm, s.profile)
            Screen.Sources -> SourcesScreen(vm)
            is Screen.SourceEditor -> SourceEditorScreen(vm, s.source)
            Screen.Channels -> ChannelsScreen(vm)
            is Screen.Player -> PlayerScreen(vm, s.index)
        }
    }
}

// ───────────────────────── Acceso ─────────────────────────

@Composable
fun LoginScreen(vm: AppViewModel) {
    val t = V.type
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit(create: Boolean) {
        if (busy) return
        busy = true
        error = null
        vm.signIn(email, pass, create) {
            busy = false
            error = it
        }
    }

    Box(Modifier.fillMaxSize().padding(64.px), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.width(640.px), radius = 24.px, fill = 0.06f, rim = 0.16f) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(48.px),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VisionMark(88.px)
                Spacer(Modifier.height(16.px))
                Text("Vision", style = t.displayHero, color = Color.White)
                Spacer(Modifier.height(4.px))
                Text(
                    "Tus perfiles y favoritos, en todas tus pantallas.",
                    style = t.bodyLg, color = Dim, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.px))
                GlassField(email, { email = it }, "Correo", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
                Spacer(Modifier.height(16.px))
                GlassField(
                    pass, { pass = it }, "Contraseña", Modifier.fillMaxWidth(),
                    password = true, imeAction = ImeAction.Done,
                )
                error?.let {
                    Spacer(Modifier.height(16.px))
                    Text(it, style = t.bodyMd, color = ErrorTint, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(32.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton(if (busy) "Un momento…" else "Entrar", primary = true) { submit(false) }
                    TvButton("Crear cuenta") { submit(true) }
                }
            }
        }
    }
}

// ───────────────────────── Perfiles ─────────────────────────

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val t = V.type
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }

    Box(Modifier.fillMaxSize().padding(64.px)) {
        Wordmark(Modifier.align(Alignment.TopStart))

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (editing) "Administrar perfiles" else "¿Quién está viendo?",
                style = t.headlineXl, color = Color.White,
            )
            Spacer(Modifier.height(8.px))
            Text(
                if (editing) "Elige un perfil para editarlo" else "Cada perfil guarda sus propios favoritos",
                style = t.bodyLg, color = Dim,
            )
            Spacer(Modifier.height(24.px))

            val list = profiles
            if (list == null) {
                Spacer(Modifier.height(120.px))
                Text("Cargando…", style = t.bodyLg, color = Dim)
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.px, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 48.px, vertical = 48.px),
                ) {
                    items(list, key = { it.id }) { p ->
                        ProfileCard(p, editing) {
                            when {
                                editing -> vm.push(Screen.ProfileEditor(p))
                                p.pinHash.isNotEmpty() -> pinFor = p
                                else -> vm.chooseProfile(p)
                            }
                        }
                    }
                    item {
                        Focusable(
                            onClick = { vm.push(Screen.ProfileEditor(null)) },
                            modifier = Modifier.width(220.px).height(264.px),
                            radius = 24.px,
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(128.px)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+", style = t.displayHero, color = if (focused) Color.White else Dim)
                                }
                                Spacer(Modifier.height(20.px))
                                Text(
                                    "Añadir perfil", style = t.bodyXl,
                                    color = if (focused) Color.White else Dim,
                                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.px))
                                Text(" ", style = t.labelMd)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(16.px)) {
            TvButton(if (editing) "Listo" else "Administrar perfiles", selected = editing) { editing = !editing }
            TvButton("Fuentes IPTV") { vm.push(Screen.Sources) }
            TvButton("Cerrar sesión") { vm.signOut() }
        }
    }

    pinFor?.let { p ->
        PinDialog(
            profile = p,
            onDismiss = { pinFor = null },
            onOk = {
                pinFor = null
                vm.chooseProfile(p)
            },
        )
    }
}

@Composable
private fun ProfileCard(p: Profile, editing: Boolean, onClick: () -> Unit) {
    val t = V.type
    Focusable(
        onClick = onClick,
        modifier = Modifier.width(220.px).height(264.px),
        radius = 24.px,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(p.avatar, p.name.take(1).uppercase(), 128.px)
            Spacer(Modifier.height(20.px))
            Text(
                p.name,
                Modifier.padding(horizontal = 12.px),
                style = t.bodyXl,
                color = if (focused) Color.White else Dim,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.px))
            Text(
                when {
                    editing -> "Editar"
                    p.pinHash.isNotEmpty() -> "Con PIN"
                    else -> " "
                },
                style = t.labelMd, color = Faint,
            )
        }
    }
}

@Composable
private fun PinDialog(profile: Profile, onDismiss: () -> Unit, onOk: () -> Unit) {
    val t = V.type
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            Modifier.width(520.px),
            radius = 24.px,
            brush = SolidColor(Layer2.copy(alpha = 0.96f)),
            rim = 0.20f,
        ) {
            Column(Modifier.padding(40.px), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(profile.avatar, profile.name.take(1).uppercase(), 72.px)
                Spacer(Modifier.height(16.px))
                Text("PIN de ${profile.name}", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(24.px))
                GlassField(
                    pin, { pin = it.filter(Char::isDigit).take(4); error = false }, "PIN de 4 dígitos",
                    Modifier.fillMaxWidth(), password = true, keyboard = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done, focusRequester = focus,
                )
                if (error) {
                    Spacer(Modifier.height(12.px))
                    Text("PIN incorrecto", style = t.bodyMd, color = ErrorTint)
                }
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Entrar", primary = true) { if (sha256(pin) == profile.pinHash) onOk() else error = true }
                    TvButton("Cancelar") { onDismiss() }
                }
            }
        }
    }
}

@Composable
fun ProfileEditorScreen(vm: AppViewModel, existing: Profile?) {
    val t = V.type
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var avatar by remember { mutableIntStateOf(existing?.avatar ?: 0) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val hasPin = existing?.pinHash?.isNotEmpty() == true

    Box(Modifier.fillMaxSize().padding(64.px), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.width(960.px), radius = 24.px, fill = 0.06f, rim = 0.16f) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(48.px)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(avatar, name.trim().take(1).uppercase().ifEmpty { "?" }, 72.px)
                    Spacer(Modifier.width(24.px))
                    Text(
                        if (existing == null) "Nuevo perfil" else "Editar perfil",
                        style = t.headlineLg, color = Color.White,
                    )
                }
                Spacer(Modifier.height(32.px))
                GlassField(name, { name = it.take(20) }, "Nombre", Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.px))
                Text("Color", style = t.labelMd, color = Dim)
                Spacer(Modifier.height(8.px))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.px),
                    contentPadding = PaddingValues(vertical = 12.px, horizontal = 8.px),
                ) {
                    items(AvatarGradients.size) { i ->
                        val selected = avatar == i
                        Focusable(
                            onClick = { avatar = i },
                            modifier = Modifier.size(64.px),
                            radius = Pill,
                            brush = avatarBrush(i),
                            rim = if (selected) 0.95f else 0.20f,
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Box(Modifier.size(14.px).background(Color.White, CircleShape))
                        }
                    }
                }
                Spacer(Modifier.height(16.px))
                GlassField(
                    pin, { pin = it.filter(Char::isDigit).take(4) },
                    if (hasPin) "Nuevo PIN de 4 dígitos (vacío = conservar el actual)" else "PIN opcional de 4 dígitos",
                    Modifier.fillMaxWidth(), password = true, keyboard = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                )
                error?.let {
                    Spacer(Modifier.height(16.px))
                    Text(it, style = t.bodyMd, color = ErrorTint)
                }
                Spacer(Modifier.height(32.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Guardar", primary = true) {
                        val clean = name.trim()
                        when {
                            clean.isEmpty() -> error = "Escribe un nombre."
                            pin.isNotEmpty() && pin.length != 4 -> error = "El PIN debe tener 4 dígitos."
                            else -> {
                                val hash = when {
                                    removePin -> ""
                                    pin.length == 4 -> sha256(pin)
                                    else -> existing?.pinHash ?: ""
                                }
                                vm.saveProfile(
                                    Profile(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        name = clean,
                                        avatar = avatar,
                                        pinHash = hash,
                                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                    )
                                )
                                vm.pop()
                            }
                        }
                    }
                    if (hasPin) {
                        TvButton(if (removePin) "Se quitará el PIN" else "Quitar PIN", selected = removePin) {
                            removePin = !removePin
                        }
                    }
                    if (existing != null) {
                        TvButton(if (confirmDelete) "¿Seguro? Pulsa otra vez" else "Eliminar", selected = confirmDelete) {
                            if (confirmDelete) vm.deleteProfile(existing.id) else confirmDelete = true
                        }
                    }
                    TvButton("Cancelar") { vm.pop() }
                }
            }
        }
    }
}

// ───────────────────────── Fuentes ─────────────────────────

@Composable
fun SourcesScreen(vm: AppViewModel) {
    val t = V.type
    val sources by vm.sources.collectAsStateWithLifecycle()
    val hasProfile = vm.activeProfile.collectAsStateWithLifecycle().value != null

    Column(Modifier.fillMaxSize().padding(64.px)) {
        Wordmark()
        Spacer(Modifier.height(32.px))
        Text("Fuentes IPTV", style = t.headlineLg, color = Color.White)
        Spacer(Modifier.height(4.px))
        Text(
            if (hasProfile) "Elige la lista que quieres ver" else "Listas guardadas en tu cuenta",
            style = t.bodyLg, color = Dim,
        )
        Spacer(Modifier.height(16.px))

        val list = sources
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> Text("Cargando…", style = t.bodyLg, color = Dim)
                list.isEmpty() -> Text(
                    "Aún no tienes fuentes. Añade una con el botón de abajo.",
                    Modifier.padding(top = 16.px), style = t.bodyLg, color = Dim,
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(20.px),
                    contentPadding = PaddingValues(vertical = 24.px, horizontal = 24.px),
                ) {
                    items(list, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.px),
                        ) {
                            Focusable(
                                onClick = { if (hasProfile) vm.openSource(s) else vm.push(Screen.SourceEditor(s)) },
                                modifier = Modifier.weight(1f).heightIn(min = 88.px),
                                radius = 20.px,
                                scale = 0.025f,
                                contentAlignment = Alignment.CenterStart,
                            ) { focused ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 28.px, vertical = 18.px),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        s.name,
                                        Modifier.weight(1f),
                                        style = t.bodyXl,
                                        color = if (focused) Color.White else Dim,
                                        fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Badge(
                                        if (s.type == SourceType.XTREAM) "XTREAM CODES" else "LISTA M3U",
                                        BadgeTone.Accent,
                                    )
                                }
                            }
                            TvButton("Editar") { vm.push(Screen.SourceEditor(s)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.px))
        Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
            TvButton("Añadir fuente", primary = true) { vm.push(Screen.SourceEditor(null)) }
            TvButton("Volver") { vm.pop() }
        }
    }
}

@Composable
fun SourceEditorScreen(vm: AppViewModel, existing: Source?) {
    val t = V.type
    var type by remember { mutableStateOf(existing?.type ?: SourceType.XTREAM) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(64.px), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.width(960.px), radius = 24.px, fill = 0.06f, rim = 0.16f) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(48.px)) {
                Text(
                    if (existing == null) "Nueva fuente" else "Editar fuente",
                    style = t.headlineLg, color = Color.White,
                )
                Spacer(Modifier.height(24.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Xtream Codes", selected = type == SourceType.XTREAM) { type = SourceType.XTREAM }
                    TvButton("Lista M3U", selected = type == SourceType.M3U) { type = SourceType.M3U }
                }
                Spacer(Modifier.height(24.px))
                GlassField(name, { name = it }, "Nombre (ej. Mi proveedor)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.px))
                if (type == SourceType.XTREAM) {
                    GlassField(
                        url, { url = it }, "URL del servidor (ej. http://servidor.com:8080)",
                        Modifier.fillMaxWidth(), keyboard = KeyboardType.Uri,
                    )
                    Spacer(Modifier.height(16.px))
                    GlassField(user, { user = it }, "Usuario", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.px))
                    GlassField(
                        pass, { pass = it }, "Contraseña", Modifier.fillMaxWidth(),
                        password = true, imeAction = ImeAction.Done,
                    )
                } else {
                    GlassField(
                        url, { url = it }, "URL de la lista (.m3u / .m3u8)", Modifier.fillMaxWidth(),
                        keyboard = KeyboardType.Uri, imeAction = ImeAction.Done,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(16.px))
                    Text(it, style = t.bodyMd, color = ErrorTint)
                }
                Spacer(Modifier.height(32.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton(
                        if (busy) "Conectando…" else if (type == SourceType.XTREAM) "Probar y guardar" else "Guardar",
                        primary = true,
                    ) {
                        if (busy) return@TvButton
                        val missing = url.isBlank() || (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank()))
                        if (missing) {
                            error = "Completa todos los datos."
                            return@TvButton
                        }
                        busy = true
                        error = null
                        vm.saveSource(
                            Source(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                type = type,
                                name = name.trim().ifEmpty { if (type == SourceType.XTREAM) "Xtream" else "Lista M3U" },
                                url = url,
                                username = if (type == SourceType.XTREAM) user.trim() else "",
                                password = if (type == SourceType.XTREAM) pass else "",
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            )
                        ) { err ->
                            busy = false
                            error = err
                        }
                    }
                    if (existing != null) {
                        TvButton(if (confirmDelete) "¿Seguro? Pulsa otra vez" else "Eliminar", selected = confirmDelete) {
                            if (confirmDelete) vm.deleteSource(existing.id) else confirmDelete = true
                        }
                    }
                    TvButton("Cancelar") { vm.pop() }
                }
            }
        }
    }
}
