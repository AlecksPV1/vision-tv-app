package com.vision.tv.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedReader
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

const val UA =
    "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Vision/1.0"

/**
 * DNS con respaldo: primero usa el del sistema (el de tu router). Si no logra resolver el nombre
 * (por ejemplo porque tu proveedor de internet bloquea el dominio del servicio IPTV), pregunta
 * por HTTPS a Cloudflare (1.1.1.1) y a Google (8.8.8.8). Funciona tanto para la API como para el video.
 */
object FallbackDns : Dns {
    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            cache[hostname]?.let { return it }
            val found = dohLookup(hostname)
            if (found.isNotEmpty()) {
                cache[hostname] = found
                return found
            }
            throw e
        }
    }

    private fun dohLookup(host: String): List<InetAddress> {
        val name = URLEncoder.encode(host, "UTF-8")
        for (endpoint in listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")) {
            try {
                val c = URL("$endpoint?name=$name&type=A").openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 5_000
                c.setRequestProperty("Accept", "application/dns-json")
                val body = try {
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
                val answers = JSONObject(body).optJSONArray("Answer") ?: continue
                val out = ArrayList<InetAddress>()
                for (i in 0 until answers.length()) {
                    val a = answers.getJSONObject(i)
                    // type 1 = registro A (IPv4); los CNAME (type 5) se ignoran
                    if (a.optInt("type") == 1) out += InetAddress.getByName(a.getString("data"))
                }
                if (out.isNotEmpty()) return out
            } catch (e: Exception) {
                // probamos con el siguiente servidor DNS
            }
        }
        return emptyList()
    }
}

object Net {
    /** Cliente compartido por la API, las listas M3U y el reproductor. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(FallbackDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

object Http {
    fun getText(url: String): String = guarded(url) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El servidor respondió HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    /** Traduce errores de red a mensajes que se entienden en pantalla (con el servidor intentado). */
    fun <T> guarded(url: String, block: () -> T): T = try {
        block()
    } catch (e: UnknownHostException) {
        val host = runCatching { URL(url).host }.getOrDefault(url)
        throw IOException(
            "No se encontró el servidor «$host», ni con DNS alternos. " +
                "Revisa que la dirección sea exactamente la que te dio tu proveedor."
        )
    } catch (e: SocketTimeoutException) {
        throw IOException("El servidor tardó demasiado en responder.")
    } catch (e: ConnectException) {
        throw IOException("No se pudo conectar con el servidor (puerto cerrado o bloqueado).")
    } catch (e: IllegalArgumentException) {
        throw IOException("La dirección del servidor no es válida.")
    }
}

/** Acepta "servidor.com:8080", "http://servidor.com:8080/" o un enlace get.php completo. */
fun normalizeBase(raw: String): String {
    // Sin espacios, y con "Http://" (mayúscula del teclado) o sin esquema también funciona.
    var u = raw.filter { !it.isWhitespace() }
    val scheme = Regex("^https?://", RegexOption.IGNORE_CASE).find(u)
    u = if (scheme != null) scheme.value.lowercase() + u.substring(scheme.value.length) else "http://$u"
    for (marker in listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php")) {
        val i = u.indexOf(marker)
        if (i > 0) u = u.substring(0, i)
    }
    return u.trimEnd('/')
}

// ───────────── Lectura en streaming de listas JSON grandes ─────────────
// Un catálogo de películas puede pesar decenas de MB. Se lee elemento por elemento para no
// cargar todo el texto en memoria (los Fire Stick tienen poca).

private fun JsonReader.readFlat(): Map<String, String> {
    val m = HashMap<String, String>(16)
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> m[name] = nextString()
            JsonToken.BOOLEAN -> m[name] = nextBoolean().toString()
            JsonToken.NULL -> nextNull()
            else -> skipValue()
        }
    }
    endObject()
    return m
}

private fun <T> streamArray(url: String, each: (Map<String, String>) -> T?): List<T> = Http.guarded(url) {
    val req = Request.Builder().url(url).header("User-Agent", UA).build()
    Net.client.newCall(req).execute().use { r ->
        if (!r.isSuccessful) throw IOException("El servidor respondió HTTP ${r.code}")
        val body = r.body ?: throw IOException("El servidor respondió vacío")
        JsonReader(body.charStream().buffered()).use { jr ->
            val out = ArrayList<T>()
            if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                jr.beginArray()
                while (jr.hasNext()) {
                    if (jr.peek() == JsonToken.BEGIN_OBJECT) each(jr.readFlat())?.let { out += it } else jr.skipValue()
                }
                jr.endArray()
            }
            out
        }
    }
}

/** org.json devuelve "null" como texto para valores nulos; esto lo evita. */
private fun JSONObject.str(name: String): String {
    val v = opt(name)
    return if (v == null || v == JSONObject.NULL) "" else v.toString()
}

class XtreamClient(rawUrl: String, private val user: String, private val pass: String) {
    private val base = normalizeBase(rawUrl)

    private fun api(action: String? = null): String {
        val q = "username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
        return "$base/player_api.php?$q" + (action?.let { "&action=$it" } ?: "")
    }

    /** Valida usuario/contraseña y devuelve vencimiento y conexiones (máximas y activas). */
    suspend fun account(): AccountInfo = withContext(Dispatchers.IO) {
        val root = JSONObject(Http.getText(api()))
        val info = root.optJSONObject("user_info") ?: error("Respuesta inválida del servidor")
        if (info.optString("auth") == "0") error("Usuario o contraseña incorrectos")
        AccountInfo(
            status = info.str("status"),
            expDate = info.str("exp_date").toLongOrNull(),
            maxConnections = info.str("max_connections").toIntOrNull() ?: 0,
            activeConnections = info.str("active_cons").toIntOrNull() ?: 0,
        )
    }

    private fun categories(action: String): Map<String, String> =
        streamArray<Pair<String, String>>(api(action)) { m ->
            val id = m["category_id"]
            if (id == null) null else id to (m["category_name"] ?: "")
        }.toMap()

    // ── TV en vivo ──
    suspend fun liveChannels(sourceId: String): List<Media> = withContext(Dispatchers.IO) {
        val cats = categories("get_live_categories")
        val out = streamArray<Media>(api("get_live_streams")) { m ->
            val id = m["stream_id"]
            if (id == null) null else Media(
                key = "${sourceId}_$id",
                name = m["name"] ?: "",
                logo = m["stream_icon"]?.ifBlank { null },
                group = m["category_id"]?.let { cats[it] } ?: "Otros",
                streamUrl = "$base/live/$user/$pass/$id.m3u8",
                itemId = id,
            )
        }
        if (out.isEmpty()) error("El servidor no devolvió canales")
        out
    }

    // ── Películas ──
    suspend fun movies(sourceId: String): List<Media> = withContext(Dispatchers.IO) {
        val cats = categories("get_vod_categories")
        val out = streamArray<Media>(api("get_vod_streams")) { m ->
            val id = m["stream_id"]
            if (id == null) null else {
                val ext = (m["container_extension"] ?: "").ifBlank { "mp4" }
                Media(
                    key = "${sourceId}_m$id",
                    name = m["name"] ?: "",
                    logo = m["stream_icon"]?.ifBlank { null },
                    group = m["category_id"]?.let { cats[it] } ?: "Otros",
                    streamUrl = "$base/movie/$user/$pass/$id.$ext",
                    kind = MediaKind.MOVIE,
                    itemId = id,
                    ext = ext,
                    rating = (m["rating"] ?: "").take(4),
                )
            }
        }
        if (out.isEmpty()) error("El servidor no devolvió películas")
        out
    }

    // ── Series ──
    suspend fun seriesList(sourceId: String): List<Media> = withContext(Dispatchers.IO) {
        val cats = categories("get_series_categories")
        val out = streamArray<Media>(api("get_series")) { m ->
            val id = m["series_id"]
            if (id == null) null else Media(
                key = "${sourceId}_s$id",
                name = m["name"] ?: "",
                logo = m["cover"]?.ifBlank { null },
                group = m["category_id"]?.let { cats[it] } ?: "Otros",
                streamUrl = "",
                kind = MediaKind.SERIES,
                itemId = id,
                year = (m["releaseDate"] ?: m["release_date"] ?: m["year"] ?: "").take(4),
                rating = (m["rating"] ?: "").take(4),
            )
        }
        if (out.isEmpty()) error("El servidor no devolvió series")
        out
    }

    /** Sinopsis y datos de una película. */
    suspend fun vodInfo(m: Media): DetailState = withContext(Dispatchers.IO) {
        val root = JSONObject(Http.getText(api("get_vod_info") + "&vod_id=${m.itemId}"))
        val info = root.optJSONObject("info") ?: JSONObject()
        DetailState(
            plot = info.str("plot").ifBlank { info.str("description") },
            genre = info.str("genre"),
            year = info.str("releasedate").ifBlank { info.str("releaseDate") }.take(4),
            rating = info.str("rating").take(4),
            durationSec = info.str("duration_secs").toIntOrNull() ?: 0,
            cover = info.str("movie_image").ifBlank { null },
        )
    }

    /** Sinopsis, temporadas y episodios de una serie. */
    suspend fun seriesInfo(sourceId: String, s: Media): DetailState = withContext(Dispatchers.IO) {
        val root = JSONObject(Http.getText(api("get_series_info") + "&series_id=${s.itemId}"))
        val info = root.optJSONObject("info") ?: JSONObject()
        val bySeason = java.util.TreeMap<Int, MutableList<Media>>()

        fun addEpisodes(season: Int, arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.str("id")
                if (id.isBlank()) continue
                val num = e.str("episode_num").toIntOrNull() ?: (i + 1)
                val ext = e.str("container_extension").ifBlank { "mp4" }
                val dur = e.optJSONObject("info")?.str("duration_secs")?.toIntOrNull() ?: 0
                bySeason.getOrPut(season) { ArrayList() } += Media(
                    key = "${sourceId}_e$id",
                    name = e.str("title").ifBlank { "Episodio $num" },
                    logo = s.logo,
                    group = s.name,
                    streamUrl = "$base/series/$user/$pass/$id.$ext",
                    kind = MediaKind.EPISODE,
                    itemId = id,
                    ext = ext,
                    seriesId = s.itemId,
                    seriesName = s.name,
                    season = season,
                    episode = num,
                    durationSec = dur,
                )
            }
        }

        when (val eps = root.opt("episodes")) {
            is JSONObject -> {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = eps.optJSONArray(k) ?: continue
                    addEpisodes(k.toIntOrNull() ?: 0, arr)
                }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                val arr = eps.optJSONArray(i) ?: continue
                addEpisodes(i + 1, arr)
            }
            else -> Unit
        }
        if (bySeason.isEmpty()) error("Esta serie no tiene episodios disponibles")

        val sorted = bySeason.mapValues { (_, list) -> list.sortedBy { it.episode } }
        DetailState(
            plot = info.str("plot"),
            genre = info.str("genre"),
            year = info.str("releaseDate").ifBlank { info.str("release_date") }.take(4),
            rating = info.str("rating").take(4),
            cover = info.str("cover").ifBlank { null },
            seasons = sorted.keys.toList(),
            episodes = sorted,
        )
    }
}

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(sourceId: String, input: BufferedReader): List<Media> {
        val out = ArrayList<Media>()
        var name = ""
        var logo: String? = null
        var group = "Sin grupo"
        var pending = false

        input.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    // El nombre va después de la última comilla y de la coma siguiente.
                    val from = line.lastIndexOf('"').coerceAtLeast(0)
                    val comma = line.indexOf(',', from)
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    name = title.ifEmpty { attrs["tvg-name"] ?: "Canal" }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"]?.ifBlank { null } ?: "Sin grupo"
                    pending = true
                }
                pending && line.isNotEmpty() && !line.startsWith("#") -> {
                    out += Media(
                        key = "${sourceId}_${line.hashCode().toUInt()}",
                        name = name,
                        logo = logo,
                        group = group,
                        streamUrl = line,
                    )
                    pending = false
                }
            }
        }
        return out
    }
}

suspend fun loadM3u(sourceId: String, url: String): List<Media> = withContext(Dispatchers.IO) {
    val target = url.filter { !it.isWhitespace() }
    Http.guarded(target) {
        val req = Request.Builder().url(target).header("User-Agent", UA).build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El servidor respondió HTTP ${r.code}")
            val body = r.body ?: throw IOException("La lista llegó vacía")
            val list = body.byteStream().bufferedReader().use { M3uParser.parse(sourceId, it) }
            if (list.isEmpty()) error("La lista no contiene canales")
            list
        }
    }
}
