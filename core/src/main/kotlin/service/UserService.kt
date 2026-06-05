package service

import model.FriendRequest
import model.Friendship
import model.Status
import model.User
import model.UserUpdate
import repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

class UserService(
    private val repository: UserRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    fun createUser(name: String, email: String, status: Status, interest: String, department: String, room: String): User {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            status = status,
            interest = interest,
            department = department,
            room = room,
            profilePicture = ""
        )
        repository.create(user)
        return user
    }

    fun getUser(id: String): User? = repository.getById(id)

    fun deleteUser(id: String) = repository.delete(id)

    fun updateUser(userId: String, update: UserExposedProperty) = repository.updateUser(userId, update)

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
