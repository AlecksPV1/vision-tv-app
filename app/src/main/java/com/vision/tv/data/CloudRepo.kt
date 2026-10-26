package com.vision.tv.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Todo vive bajo users/{uid}. Firestore guarda una copia local (funciona sin internet)
 * y sincroniza solo, así que las escrituras no se esperan: se ven al instante.
 *
 *   users/{uid}/profiles/{id}                       nombre, avatar, PIN (hash)
 *   users/{uid}/profiles/{id}/favorites/{key}       favoritos (canales, películas y series)
 *   users/{uid}/profiles/{id}/watchlist/{key}       "Mi lista" (ver después)
 *   users/{uid}/profiles/{id}/recents/{key}         últimos canales en vivo
 *   users/{uid}/profiles/{id}/progress/{key}        dónde se quedó en películas y episodios
 *   users/{uid}/sources/{id}                        listas Xtream / M3U de la cuenta
 */
class CloudRepo(uid: String) {
    private val root = FirebaseFirestore.getInstance().collection("users").document(uid)
    private fun profileDoc(id: String) = root.collection("profiles").document(id)

    private fun <T> Query.watch(map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = this@watch.addSnapshotListener { snap, _ ->
            trySend(if (snap != null) snap.documents.map(map) else emptyList())
        }
        awaitClose { reg.remove() }
    }

    // ---- Perfiles ----
    fun profiles(): Flow<List<Profile>> =
        root.collection("profiles").orderBy("createdAt").watch { d ->
            Profile(
                id = d.id,
                name = d.getString("name") ?: "?",
                avatar = (d.getLong("avatar") ?: 0L).toInt(),
                pinHash = d.getString("pinHash") ?: "",
                createdAt = d.getLong("createdAt") ?: 0L,
                emoji = d.getString("emoji") ?: "",
            )
        }

    fun saveProfile(p: Profile) {
        profileDoc(p.id).set(
            mapOf(
                "name" to p.name,
                "avatar" to p.avatar,
                "pinHash" to p.pinHash,
                "createdAt" to p.createdAt,
                "emoji" to p.emoji,
            )
        )
    }

    fun deleteProfile(id: String) {
        // Firestore no borra subcolecciones solo: limpiamos lo que cuelga del perfil.
        for (sub in listOf("favorites", "watchlist", "recents", "progress")) {
            profileDoc(id).collection(sub).get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
            }
        }
        profileDoc(id).delete()
    }

    // ---- Fuentes (listas IPTV) ----
    fun sources(): Flow<List<Source>> =
        root.collection("sources").orderBy("createdAt").watch { d ->
            Source(
                id = d.id,
                type = if (d.getString("type") == "M3U") SourceType.M3U else SourceType.XTREAM,
                name = d.getString("name") ?: "Lista",
                url = d.getString("url") ?: "",
                username = d.getString("username") ?: "",
                password = d.getString("password") ?: "",
                createdAt = d.getLong("createdAt") ?: 0L,
            )
        }

    fun saveSource(s: Source) {
        root.collection("sources").document(s.id).set(
            mapOf(
                "type" to s.type.name,
                "name" to s.name,
                "url" to s.url,
                "username" to s.username,
                "password" to s.password,
                "createdAt" to s.createdAt,
            )
        )
    }

    fun deleteSource(id: String) {
        root.collection("sources").document(id).delete()
    }

    // ---- Conversión Media <-> documento ----
    private fun toMedia(d: DocumentSnapshot) = Media(
        key = d.id,
        name = d.getString("name") ?: "",
        logo = d.getString("logo"),
        group = d.getString("group") ?: "",
        streamUrl = d.getString("streamUrl") ?: "",
        // los favoritos antiguos no traen "kind": eran canales en vivo
        kind = runCatching { MediaKind.valueOf(d.getString("kind") ?: "LIVE") }.getOrDefault(MediaKind.LIVE),
        itemId = d.getString("itemId") ?: "",
        ext = d.getString("ext") ?: "",
        seriesId = d.getString("seriesId") ?: "",
        seriesName = d.getString("seriesName") ?: "",
        season = (d.getLong("season") ?: 0L).toInt(),
        episode = (d.getLong("episode") ?: 0L).toInt(),
        year = d.getString("year") ?: "",
        rating = d.getString("rating") ?: "",
        durationSec = (d.getLong("durationSec") ?: 0L).toInt(),
    )

    private fun mediaMap(c: Media, extra: Map<String, Any?>): Map<String, Any?> = mapOf(
        "name" to c.name,
        "logo" to c.logo,
        "group" to c.group,
        "streamUrl" to c.streamUrl,
        "kind" to c.kind.name,
        "itemId" to c.itemId,
        "ext" to c.ext,
        "seriesId" to c.seriesId,
        "seriesName" to c.seriesName,
        "season" to c.season,
        "episode" to c.episode,
        "year" to c.year,
        "rating" to c.rating,
        "durationSec" to c.durationSec,
    ) + extra

    // ---- Favoritos (por perfil) ----
    fun favorites(profileId: String): Flow<List<Media>> =
        profileDoc(profileId).collection("favorites")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .watch(::toMedia)

    fun addFavorite(profileId: String, c: Media) {
        profileDoc(profileId).collection("favorites").document(c.key)
            .set(mediaMap(c, mapOf("addedAt" to System.currentTimeMillis())))
    }

    fun removeFavorite(profileId: String, key: String) {
        profileDoc(profileId).collection("favorites").document(key).delete()
    }

    // ---- Mi lista / ver después (por perfil) ----
    fun watchlist(profileId: String): Flow<List<Media>> =
        profileDoc(profileId).collection("watchlist")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .watch(::toMedia)

    fun addWatchlist(profileId: String, c: Media) {
        profileDoc(profileId).collection("watchlist").document(c.key)
            .set(mediaMap(c, mapOf("addedAt" to System.currentTimeMillis())))
    }

    fun removeWatchlist(profileId: String, key: String) {
        profileDoc(profileId).collection("watchlist").document(key).delete()
    }

    // ---- Últimos canales en vivo (por perfil) ----
    fun recents(profileId: String): Flow<List<Media>> =
        profileDoc(profileId).collection("recents")
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(25)
            .watch(::toMedia)

    fun addRecent(profileId: String, c: Media) {
        profileDoc(profileId).collection("recents").document(c.key)
            .set(mediaMap(c, mapOf("watchedAt" to System.currentTimeMillis())))
    }

    // ---- Progreso en películas y episodios (por perfil) ----
    fun progress(profileId: String): Flow<List<Progress>> =
        profileDoc(profileId).collection("progress")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(150)
            .watch { d ->
                Progress(
                    media = toMedia(d),
                    positionMs = d.getLong("positionMs") ?: 0L,
                    durationMs = d.getLong("durationMs") ?: 0L,
                    updatedAt = d.getLong("updatedAt") ?: 0L,
                    hidden = d.getBoolean("hidden") ?: false,
                )
            }

    /**
     * Quita de "Continuar viendo" una película o, si es un episodio, la serie completa.
     * Solo marca los documentos: el avance y los "VISTO" se conservan, y como [saveProgress]
     * reescribe el documento sin la marca, al volver a verlo reaparece solo.
     */
    fun hideProgress(profileId: String, m: Media) {
        val col = profileDoc(profileId).collection("progress")
        if (m.kind != MediaKind.EPISODE) {
            col.document(m.key).update("hidden", true)
            return
        }
        // Los ids de episodio son "{fuente}_e{id}": el prefijo evita tocar otra fuente con el mismo seriesId.
        val prefix = m.key.substringBefore("_e") + "_e"
        col.whereEqualTo("seriesId", m.seriesId).get().addOnSuccessListener { snap ->
            val batch = FirebaseFirestore.getInstance().batch()
            snap.documents.filter { it.id.startsWith(prefix) }.forEach { batch.update(it.reference, "hidden", true) }
            batch.commit()
        }
    }

    fun saveProgress(profileId: String, p: Progress) {
        profileDoc(profileId).collection("progress").document(p.media.key).set(
            mediaMap(
                p.media,
                mapOf(
                    "positionMs" to p.positionMs,
                    "durationMs" to p.durationMs,
                    "updatedAt" to p.updatedAt,
                ),
            )
        )
    }
}
