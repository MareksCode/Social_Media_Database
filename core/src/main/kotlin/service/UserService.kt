package service

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import repository.UserRepository
import java.util.UUID

class UserService(private val repository: UserRepository) {
    fun createUser(name: String, email: String, status: Status, interest: String, department: String, room: String): User {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            status = status,
            interest = interest,
            department = department,
            room = room,
            profilePicture = null
        )
        repository.create(user)
        return user
    }

    fun getUser(id: String): User? = repository.getById(id)

    fun deleteUser(id: String) = repository.delete(id)

    fun updateUser(userId: String, property: UserExposedProperty, value: Any?) {
        when (property) {
            UserExposedProperty.NAME, UserExposedProperty.EMAIL,
            UserExposedProperty.INTEREST, UserExposedProperty.DEPARTMENT,
            UserExposedProperty.ROOM -> require(value is String) { "Expected non-null String for $property" }
            UserExposedProperty.STATUS -> require(value is Status) { "Expected Status for $property" }
            UserExposedProperty.PROFILE_PICTURE -> require(value == null || value is String) { "Expected String? for PROFILE_PICTURE" }
        }
        repository.updateProperty(userId, property, value)
    }

    fun get(userId: String, property: UserExposedProperty): Any? = repository.getProperty(userId, property)

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
