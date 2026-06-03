package model

import java.time.Instant

data class Friendship(
    val friend: User,
    val createTime: Instant
)
