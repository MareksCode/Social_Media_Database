package model

import java.time.Instant

data class Friendship(
    val userId1: String,
    val userId2: String,
    val createTime: Instant
) {
    // returns the other participant's id
    fun other(userId: String): String =
        if (userId == userId1) userId2 else userId1
}
