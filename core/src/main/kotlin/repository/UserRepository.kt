package repository

import model.FriendRequest
import model.User
import model.UserExposedProperty

interface UserRepository {
    fun create(user: User)
    fun getById(id: String): User?
    fun delete(id: String)
    fun updateProperty(id: String, property: UserExposedProperty, value: Any?)
    fun getProperty(id: String, property: UserExposedProperty): Any?
    fun sendFriendRequest(fromId: String, toId: String)
    fun getPendingFriendRequests(userId: String): List<FriendRequest>
    fun getFriends(userId: String): List<User>
    fun removeFriend(userId: String, friendId: String)
    fun getFriendsOfFriends(userId: String): List<User>
}
