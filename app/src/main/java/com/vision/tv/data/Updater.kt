package com.vision.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vision.tv.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Una versión publicada en GitHub con su APK. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Busca versiones nuevas en las releases de GitHub del proyecto, descarga el APK y abre el
 * instalador del sistema. El APK nuevo debe estar firmado con la misma llave para instalarse encima.
 */
object Updater {
    private const val REPO = "AlecksPV1/vision-tv-app"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /** La release más reciente si es más nueva que la instalada; null si ya estás al día. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Vision/${BuildConfig.VERSION_NAME}")
            .build()
        val body = Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub respondió HTTP ${r.code}")
            r.body?.string() ?: ""
        }
        val root = JSONObject(body)
        val version = root.optString("tag_name").removePrefix("v").removePrefix("V")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null

        val assets = root.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return@withContext Release(
                    version = version,
                    notes = root.optString("body").trim(),
                    apkUrl = a.optString("browser_download_url"),
                    sizeBytes = a.optLong("size"),
                )
            }
        }
        null
    }

    /** Compara "1.10.0" con "1.9.2" número por número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Descarga el APK a la caché de la app; [onProgress] recibe 0..1 (o -1 si no se sabe el tamaño). */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "vision-${rel.version}.apk")
            val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "Vision/${BuildConfig.VERSION_NAME}").build()
            Net.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("La descarga falló (HTTP ${r.code})")
                val body = r.body ?: throw IOException("La descarga llegó vacía")
                val total = body.contentLength().takeIf { it > 0 } ?: rel.sizeBytes
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastReport > 256 * 1024) {
                                lastReport = done
                                onProgress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                }
            }
            onProgress(1f)
            out
        }

    /**
     * En Fire OS 7+ (Android 8+) cada app necesita permiso para instalar otras.
     * Devuelve false si falta; [openInstallPermission] lleva al ajuste correspondiente.
     */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }.onFailure {
            // Algunos Fire OS no tienen esa pantalla directa: se abren los ajustes de seguridad.
            runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Abre el instalador del sistema con el APK descargado. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
