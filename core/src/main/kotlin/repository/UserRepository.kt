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

    // friend
    fun addFriend(userId: String, friendId: String, createTime: Instant)
    fun removeFriend(userId: String, friendId: String)
    fun getFriends(userId: String): List<Friendship>
    fun getFriendsOf(userIds: Collection<String>): List<User>
}
