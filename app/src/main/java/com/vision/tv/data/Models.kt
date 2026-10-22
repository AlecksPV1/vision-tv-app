package com.vision.tv.data

data class Profile(
    val id: String,
    val name: String,
    val avatar: Int,
    val pinHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
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

data class Channel(
    val key: String,
    val name: String,
    val logo: String?,
    val group: String,
    val streamUrl: String,
)

data class AccountInfo(
    val status: String,
    val expDate: Long?,
    val maxConnections: Int,
    val activeConnections: Int,
)

data class ChannelsState(
    val loading: Boolean = false,
    val error: String? = null,
    val channels: List<Channel> = emptyList(),
    val account: AccountInfo? = null,
)
