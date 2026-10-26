package com.vision.tv.data

data class Profile(
    val id: String,
    val name: String,
    val avatar: Int,
    val pinHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Emoji que se muestra como icono del perfil; vacío = la inicial del nombre. */
    val emoji: String = "",
)

enum class SourceType { XTREAM, M3U }

data class Source(
    val id: String,
    val type: SourceType,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** LIVE = canal en vivo · MOVIE = película · SERIES = serie (como título) · EPISODE = un capítulo. */
enum class MediaKind { LIVE, MOVIE, SERIES, EPISODE }

/**
 * Cualquier cosa que se puede listar, marcar como favorita o reproducir.
 * Una serie (SERIES) no tiene streamUrl: se reproduce por episodios (EPISODE).
 */
data class Media(
    val key: String,
    val name: String,
    val logo: String?,
    val group: String,
    val streamUrl: String,
    val kind: MediaKind = MediaKind.LIVE,
    val itemId: String = "",
    val ext: String = "",
    val seriesId: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val year: String = "",
    val rating: String = "",
    val durationSec: Int = 0,
)

/** Dónde se quedó el perfil en una película o episodio. */
data class Progress(
    val media: Media,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class AccountInfo(
    val status: String,
    val expDate: Long?,
    val maxConnections: Int,
    val activeConnections: Int,
)

data class CatalogState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<Media> = emptyList(),
)

/** Ficha de una película o serie (sinopsis, temporadas y episodios). */
data class DetailState(
    val loading: Boolean = false,
    val error: String? = null,
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val durationSec: Int = 0,
    val cover: String? = null,
    val seasons: List<Int> = emptyList(),
    val episodes: Map<Int, List<Media>> = emptyMap(),
)
