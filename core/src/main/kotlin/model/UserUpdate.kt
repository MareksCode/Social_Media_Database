package model

data class UserUpdate(
    val name: String? = null,
    val email: String? = null,
    val status: Status? = null,
    val interest: String? = null,
    val department: String? = null,
    val room: String? = null,
    val profilePicture: String? = null
)
