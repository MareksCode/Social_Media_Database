package model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val status: Status,
    val interest: String,
    val abteilung: String,
    val raum: String,
    val profilbild: String?,
    val friends: List<String>
)
