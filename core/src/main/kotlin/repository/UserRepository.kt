package repository

import model.FriendRequest
import model.Friendship
import model.User
import model.UserUpdate
import java.time.Instant

interface UserRepository {
    // user
    fun create(user: User)
    fun getById(id: String): User?
    fun delete(id: String)
    fun update(id: String, update: UserUpdate)

    // friend-request
    fun addFriendRequest(fromId: String, toId: String, sendTime: Instant)
    fun removeFriendRequest(fromId: String, toId: String)
    fun friendRequestExists(fromId: String, toId: String): Boolean
    fun getIncomingFriendRequests(userId: String): List<FriendRequest>

    /**
     * Atomically consumes the pending request [toId] -> [fromId] (if present) and
     * creates the bidirectional friendship between [fromId] and [toId].
     * No-op if either user does not exist.
     */
    fun acceptFriendRequest(fromId: String, toId: String, createTime: Instant)

    // friend
    fun addFriend(userId: String, friendId: String, createTime: Instant)
    fun removeFriend(userId: String, friendId: String)
    fun areFriends(userId: String, friendId: String): Boolean
    fun getFriends(userId: String): List<Friendship>
    fun getFriendsOf(userIds: Collection<String>): List<User>
}
