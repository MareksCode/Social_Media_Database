package service

import model.User
import repository.UserRepository

class UserService(val repository: UserRepository) {
    fun createUser(user: User) = repository.create(user)

    fun getUser(id: String): User? = repository.getById(id)

    fun updateUser(user: User) = repository.update(user)

    fun deleteUser(id: String) = repository.delete(id)

    fun addFriend(userId: String, friendId: String) = repository.addFriend(userId, friendId)

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)

    fun getFriends(userId: String): List<User> = repository.getFriends(userId)

    fun suggestFriends(userId: String): List<User> = repository.getFriendsOfFriends(userId)
}
