package repository

import model.User

interface UserRepository {

    /** Persists a new user. */
    fun create(user: User)

    /** Returns the user with [id], or null if not found. */
    fun getById(id: String): User?

    /**
     * Replaces all fields of the existing user whose id matches [user.id].
     */
    fun update(user: User)

    /** Removes the user with [id]. */
    fun delete(id: String)

    /** Returns resolved User objects for every friend ID in the user's friends list. */
    fun getFriends(userId: String): List<User>

    /** Adds [friendId] to the friend list of the user with [userId]. */
    fun addFriend(userId: String, friendId: String)

    /** Removes [friendId] from the friend list of the user with [userId]. */
    fun removeFriend(userId: String, friendId: String)

    /**
     * Returns users who are friends of the user's friends,
     * excluding direct friends and the user themselves,
     * sorted descending by number of common friends.
     */
    fun getFriendsOfFriends(userId: String): List<User>
}
