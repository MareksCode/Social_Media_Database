package repository

import model.User

interface UserRepository {
    fun create(user: User)

    fun getById(id: String): User?

    fun update(user: User)

    fun delete(id: String)

    fun getFriends(userId: String): List<User>

    fun addFriend(userId: String, friendId: String)

    fun removeFriend(userId: String, friendId: String)

    // Returns friends of friends who are not already friends of the user, sorted by number of common friends
    fun getFriendsOfFriends(userId: String): List<User>
}
