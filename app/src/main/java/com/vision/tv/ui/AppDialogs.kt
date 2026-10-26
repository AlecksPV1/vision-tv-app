package com.vision.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vision.tv.AppViewModel
import com.vision.tv.BuildConfig
import com.vision.tv.UpdateUi
import com.vision.tv.data.MediaKind
import com.vision.tv.data.Progress
import com.vision.tv.data.Updater

/** Avisos que pueden salir sobre cualquier pantalla (menos el reproductor). */
@Composable
fun AppDialogs(vm: AppViewModel) {
    val crash by vm.lastCrash.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val hide by vm.confirmHide.collectAsStateWithLifecycle()
    val c = crash
    val h = hide
    when {
        c != null -> CrashDialog(c) { vm.dismissCrash() }
        h != null -> HideDialog(h, onConfirm = { vm.hideFromContinue(h) }, onDismiss = { vm.cancelHide() })
        update != UpdateUi.Hidden -> UpdateDialog(vm, update)
    }
}

@Composable
private fun VisionDialog(
    onDismiss: () -> Unit,
    width: Int = 860,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassSurface(
            Modifier.width(width.px),
            radius = 24.px,
            brush = SolidColor(Layer2.copy(alpha = 0.97f)),
            rim = 0.20f,
        ) {
            Column(Modifier.padding(40.px)) { content() }
        }
    }
}

@Composable
private fun CrashDialog(text: String, onDismiss: () -> Unit) {
    val t = V.type
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) { ok.requestWhenReady() }
    VisionDialog(onDismiss, width = 1100) {
        Text("Vision se cerró inesperadamente", style = t.headlineMd, color = Color.White)
        Spacer(Modifier.height(8.px))
        Text(
            "Si vuelve a pasar, toma una foto de esta pantalla: el detalle de abajo dice exactamente qué falló.",
            style = t.bodyLg, color = Dim,
        )
        Spacer(Modifier.height(20.px))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.px)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text.lineSequence().take(40).joinToString("\n"),
                style = t.labelMd.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = null),
                color = Faint,
            )
        }
        Spacer(Modifier.height(24.px))
        TvButton("Entendido", Modifier.focusRequester(ok), primary = true, onClick = onDismiss)
    }
}

@Composable
private fun HideDialog(p: Progress, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val t = V.type
    val isEpisode = p.media.kind == MediaKind.EPISODE
    val title = if (isEpisode) p.media.seriesName else p.media.name
    // El foco empieza en "Cancelar": un OK de más no debe quitar nada.
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestWhenReady() }
    VisionDialog(onDismiss, width = 760) {
        Text("¿Quitar de Continuar viendo?", style = t.headlineMd, color = Color.White)
        Spacer(Modifier.height(8.px))
        Text(
            "«$title» dejará de aparecer en Continuar viendo. " +
                (if (isEpisode) "Los episodios vistos y tu avance se conservan" else "Tu avance se conserva") +
                ", y si vuelves a reproducirla aparecerá de nuevo.",
            style = t.bodyLg, color = Dim,
        )
        Spacer(Modifier.height(28.px))
        Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
            TvButton("Quitar", primary = true, onClick = onConfirm)
            TvButton("Cancelar", Modifier.focusRequester(cancel), onClick = onDismiss)
        }
    }
}

@Composable
private fun UpdateDialog(vm: AppViewModel, ui: UpdateUi) {
    val t = V.type
    val ctx = LocalContext.current
    val primary = remember { FocusRequester() }
    // Al cambiar de paso (descargando → listo…) el foco vuelve al botón principal.
    LaunchedEffect(ui::class) { primary.requestWhenReady() }

    VisionDialog(onDismiss = { vm.dismissUpdate() }) {
        when (ui) {
            UpdateUi.Hidden -> Unit

            UpdateUi.Checking -> {
                Text("Buscando actualizaciones…", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(24.px))
                TvButton("Cancelar", Modifier.focusRequester(primary)) { vm.dismissUpdate() }
            }

            UpdateUi.UpToDate -> {
                Text("Ya tienes la última versión", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(8.px))
                Text("Vision ${BuildConfig.VERSION_NAME}", style = t.bodyLg, color = Dim)
                Spacer(Modifier.height(24.px))
                TvButton("Aceptar", Modifier.focusRequester(primary), primary = true) { vm.dismissUpdate() }
            }

            is UpdateUi.Available -> {
                Badge("NUEVA VERSIÓN", BadgeTone.Accent)
                Spacer(Modifier.height(12.px))
                Text("Vision ${ui.rel.version} está disponible", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(4.px))
                Text(
                    "Tienes la ${BuildConfig.VERSION_NAME}. Tus perfiles, favoritos y avance se conservan.",
                    style = t.bodyLg, color = Dim,
                )
                val notes = remember(ui.rel.notes) { cleanNotes(ui.rel.notes) }
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(20.px))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.px)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(notes, style = t.bodyMd, color = Dim)
                    }
                }
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Actualizar ahora", Modifier.focusRequester(primary), primary = true) {
                        vm.downloadUpdate(ctx, ui.rel)
                    }
                    TvButton("Más tarde") { vm.dismissUpdate() }
                }
            }

            is UpdateUi.Downloading -> {
                Text("Descargando Vision ${ui.rel.version}…", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(24.px))
                ProgressBar(if (ui.progress < 0f) 0f else ui.progress, Modifier.fillMaxWidth(), height = 10.px)
                Spacer(Modifier.height(10.px))
                Text(
                    if (ui.progress < 0f) "Descargando…" else "${(ui.progress * 100).toInt()} %",
                    style = t.labelLg, color = Dim,
                )
                Spacer(Modifier.height(24.px))
                TvButton("Cancelar", Modifier.focusRequester(primary)) { vm.dismissUpdate() }
            }

            is UpdateUi.NeedsPermission -> {
                Text("Falta un permiso", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(8.px))
                Text(
                    "Fire TV necesita que autorices a Vision para instalar actualizaciones. " +
                        "En la pantalla que se abrirá, activa Vision y luego vuelve con Atrás.\n\n" +
                        "Si no se abre: Ajustes → Mi Fire TV → Opciones para desarrolladores → " +
                        "Instalar apps desconocidas → Vision.",
                    style = t.bodyLg, color = Dim,
                )
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Abrir ajustes", Modifier.focusRequester(primary), primary = true) {
                        Updater.openInstallPermission(ctx)
                    }
                    TvButton("Ya lo activé, instalar") { vm.installUpdate(ctx, ui.rel, ui.apk) }
                    TvButton("Más tarde") { vm.dismissUpdate() }
                }
            }

            is UpdateUi.Ready -> {
                Text("Instalando Vision ${ui.rel.version}", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(8.px))
                Text(
                    "Confirma en la pantalla de instalación. Si la cerraste por error, vuelve a intentarlo aquí.",
                    style = t.bodyLg, color = Dim,
                )
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    TvButton("Instalar", Modifier.focusRequester(primary), primary = true) {
                        vm.installUpdate(ctx, ui.rel, ui.apk)
                    }
                    TvButton("Más tarde") { vm.dismissUpdate() }
                }
            }

            is UpdateUi.Failed -> {
                Text("No se pudo actualizar", style = t.headlineMd, color = Color.White)
                Spacer(Modifier.height(8.px))
                Text(ui.message, style = t.bodyLg, color = ErrorTint)
                Spacer(Modifier.height(28.px))
                Row(horizontalArrangement = Arrangement.spacedBy(16.px)) {
                    val rel = ui.rel
                    TvButton("Reintentar", Modifier.focusRequester(primary), primary = true) {
                        if (rel != null) vm.downloadUpdate(ctx, rel) else vm.checkUpdate(manual = true)
                    }
                    TvButton("Cerrar") { vm.dismissUpdate() }
                }
            }
        }
    }
}

/** Las notas de GitHub vienen en Markdown: se quitan asteriscos y viñetas para leerlas en la TV. */
private fun cleanNotes(md: String): String = md.lineSequence()
    .map { line ->
        line.trim()
            .replace("**", "")
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^[-*]\\s+"), "•  ")
    }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
