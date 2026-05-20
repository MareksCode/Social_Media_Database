package model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val status: Status,
    val interest: String,
    val department: String,
    val room: String,
    val profilePicture: String
)
