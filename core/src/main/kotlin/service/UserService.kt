package service

import model.User
import repository.UserRepository

class UserService(val repository: UserRepository) {
    fun createUser(user: User) = repository.create(user)

    fun getUser(id: String): User? = repository.getById(id)

    fun updateUser(user: User): Unit = TODO("not yet implemented")

    fun deleteUser(id: String) = repository.delete(id)

    fun addFriend(userId: String, friendId: String): Unit = TODO("not yet implemented")

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)

    fun getFriends(userId: String): List<User> = repository.getFriends(userId)

    fun suggestFriends(userId: String): List<User> = repository.getFriendsOfFriends(userId)
}
