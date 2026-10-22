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
 *   users/{uid}/profiles/{id}/favorites/{channel}   favoritos del perfil
 *   users/{uid}/profiles/{id}/recents/{channel}     vistos recientemente
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
            )
        }

    fun saveProfile(p: Profile) {
        profileDoc(p.id).set(
            mapOf(
                "name" to p.name,
                "avatar" to p.avatar,
                "pinHash" to p.pinHash,
                "createdAt" to p.createdAt,
            )
        )
    }

    fun deleteProfile(id: String) {
        // Firestore no borra subcolecciones solo: limpiamos favoritos y recientes.
        for (sub in listOf("favorites", "recents")) {
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

    // ---- Favoritos y recientes (por perfil) ----
    private fun toChannel(d: DocumentSnapshot) = Channel(
        key = d.id,
        name = d.getString("name") ?: "",
        logo = d.getString("logo"),
        group = d.getString("group") ?: "",
        streamUrl = d.getString("streamUrl") ?: "",
    )

    private fun channelMap(c: Channel, timeField: String) = mapOf(
        "name" to c.name,
        "logo" to c.logo,
        "group" to c.group,
        "streamUrl" to c.streamUrl,
        timeField to System.currentTimeMillis(),
    )

    fun favorites(profileId: String): Flow<List<Channel>> =
        profileDoc(profileId).collection("favorites")
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .watch(::toChannel)

    fun addFavorite(profileId: String, c: Channel) {
        profileDoc(profileId).collection("favorites").document(c.key).set(channelMap(c, "addedAt"))
    }

    fun removeFavorite(profileId: String, key: String) {
        profileDoc(profileId).collection("favorites").document(key).delete()
    }

    fun recents(profileId: String): Flow<List<Channel>> =
        profileDoc(profileId).collection("recents")
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(25)
            .watch(::toChannel)

    fun addRecent(profileId: String, c: Channel) {
        profileDoc(profileId).collection("recents").document(c.key).set(channelMap(c, "watchedAt"))
    }
}
