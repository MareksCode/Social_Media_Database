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

    fun updateUser(userId: String, update: UserUpdate) = repository.update(userId, update)

    fun sendFriendRequest(fromId: String, toId: String) {
        require(fromId != toId) { "Cannot send friend request to yourself" }
        val now = Instant.now(clock)
        if (repository.friendRequestExists(toId, fromId)) {
            // reverse request already pending
            repository.removeFriendRequest(toId, fromId)
            repository.addFriend(fromId, toId, now)
        } else {
            repository.addFriendRequest(fromId, toId, now)
        }
    }

    fun declineFriendRequest(userId: String, fromId: String) =
        repository.removeFriendRequest(fromId, userId)

    fun getPendingFriendRequests(userId: String): List<FriendRequest> =
        repository.getIncomingFriendRequests(userId)

    fun getFriends(userId: String): List<Friendship> = repository.getFriends(userId)

    fun getFriendRecommendations(userId: String): List<User> {
        val direct = repository.getFriends(userId)
        val directIds = direct.mapTo(mutableSetOf()) { it.friend.id }
        return repository.getFriendsOf(directIds)
            .filter { it.id != userId && it.id !in directIds }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)
}
