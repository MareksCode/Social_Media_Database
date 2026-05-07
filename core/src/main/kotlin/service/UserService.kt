package service

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import repository.UserRepository
import java.util.UUID

class UserService(private val repository: UserRepository) {

    fun createUser(
        name: String,
        email: String,
        status: Status,
        interest: String,
        department: String,
        room: String
    ): User {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            status = status,
            interest = interest,
            department = department,
            room = room,
            profilePicture = null,
            friends = emptyList()
        )
        repository.create(user)
        return user
    }

    fun getUser(id: String): User? = repository.getById(id)

    fun deleteUser(id: String) = repository.delete(id)

    fun change(userId: String, property: UserExposedProperty, newValue: Any?) {
        if (newValue == null) {
            require(property == UserExposedProperty.PROFILE_PICTURE) {
                "$property cannot be null"
            }
        } else {
            val expectedType: Class<*> = when (property) {
                UserExposedProperty.NAME,
                UserExposedProperty.EMAIL,
                UserExposedProperty.INTEREST,
                UserExposedProperty.DEPARTMENT,
                UserExposedProperty.ROOM,
                UserExposedProperty.PROFILE_PICTURE -> String::class.java
                UserExposedProperty.STATUS -> Status::class.java
            }
            require(expectedType.isInstance(newValue)) {
                "$property expects ${expectedType.simpleName}, got ${newValue::class.simpleName}"
            }
        }
        repository.updateProperty(userId, property, newValue)
    }

    fun get(userId: String, property: UserExposedProperty): Any? =
        repository.getProperty(userId, property)

    fun sendFriendRequest(fromId: String, toId: String) {
        require(fromId != toId) { "Cannot send friend request to yourself" }
        repository.sendFriendRequest(fromId, toId)
    }

    fun getPendingFriendRequests(userId: String): List<FriendRequest> =
        repository.getPendingFriendRequests(userId)

    fun getFriends(userId: String): List<User> = repository.getFriends(userId)

    fun suggestFriends(userId: String): List<User> = repository.getFriendsOfFriends(userId)

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)
}
