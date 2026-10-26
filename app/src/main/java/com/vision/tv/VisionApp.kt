package com.vision.tv

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vision.tv.data.Net
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VisionApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    /**
     * Pósters y logos: mismo cliente (y DNS de respaldo) que la API, colores de 16 bits (la mitad de
     * memoria; en una TV no se nota) y una caché en memoria modesta para no agotar un Fire Stick.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Net.client }
        .bitmapConfig(Bitmap.Config.RGB_565)
        .allowRgb565(true)
        .crossfade(false)
        .respectCacheHeaders(false)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(150L * 1024 * 1024)
                .build()
        }
        .build()
}

/**
 * Si la app se cierra por un error, guarda el detalle en un archivo. Al abrirla de nuevo se muestra
 * en pantalla, para saber qué pasó sin tener que conectar el Fire Stick a una computadora.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private lateinit var file: File

    fun install(app: Application) {
        file = File(app.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                file.writeText("Vision ${BuildConfig.VERSION_NAME} · $now · hilo ${thread.name}\n\n$trace")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** El último error guardado (o null si la app cerró bien). */
    fun read(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }
}
