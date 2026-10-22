package com.vision.tv.data

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val UA =
    "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Vision/1.0"

object Http {
    fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 45_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        return c
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) {
                throw IOException("El servidor respondió HTTP ${c.responseCode}")
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Acepta "servidor.com:8080", "http://servidor.com:8080/" o un enlace get.php completo. */
fun normalizeBase(raw: String): String {
    var u = raw.trim()
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    for (marker in listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php")) {
        val i = u.indexOf(marker)
        if (i > 0) u = u.substring(0, i)
    }
    return u.trimEnd('/')
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
            status = info.optString("status"),
            expDate = info.optString("exp_date").toLongOrNull(),
            maxConnections = info.optString("max_connections").toIntOrNull() ?: 0,
            activeConnections = info.optString("active_cons").toIntOrNull() ?: 0,
        )
    }

    suspend fun liveChannels(sourceId: String): List<Channel> = withContext(Dispatchers.IO) {
        val cats = HashMap<String, String>()
        val ca = JSONArray(Http.getText(api("get_live_categories")))
        for (i in 0 until ca.length()) {
            val o = ca.getJSONObject(i)
            cats[o.optString("category_id")] = o.optString("category_name")
        }
        val arr = JSONArray(Http.getText(api("get_live_streams")))
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("stream_id")
            out += Channel(
                key = "${sourceId}_$id",
                name = o.optString("name"),
                logo = o.optString("stream_icon").ifBlank { null },
                group = cats[o.optString("category_id")] ?: "Otros",
                streamUrl = "$base/live/$user/$pass/$id.m3u8",
            )
        }
        if (out.isEmpty()) error("El servidor no devolvió canales")
        out
    }
}

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(sourceId: String, input: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
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
                    out += Channel(
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

suspend fun loadM3u(sourceId: String, url: String): List<Channel> = withContext(Dispatchers.IO) {
    val c = Http.open(url.trim())
    try {
        if (c.responseCode !in 200..299) throw IOException("El servidor respondió HTTP ${c.responseCode}")
        val list = c.inputStream.bufferedReader().use { M3uParser.parse(sourceId, it) }
        if (list.isEmpty()) error("La lista no contiene canales")
        list
    } finally {
        c.disconnect()
    }
}
