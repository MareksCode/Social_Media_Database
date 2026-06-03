package model

import java.time.Instant

data class FriendRequest(
    val fromId: String,
    val toId: String,
    val sendTime: Instant
)
