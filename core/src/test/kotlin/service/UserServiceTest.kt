package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import model.Status
import model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import repository.UserRepository

class UserServiceTest {

    private lateinit var repository: UserRepository
    private lateinit var service: UserService

    private val alice = User(
        id = "1", name = "Alice", email = "alice@example.com",
        status = Status.ONLINE, interest = "coding",
        abteilung = "IT", raum = "101", profilbild = null,
        friends = emptyList()
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        service = UserService(repository)
    }

    @Test
    fun `createUser delegates to repository create`() {
        every { repository.create(alice) } returns Unit
        service.createUser(alice)
        verify(exactly = 1) { repository.create(alice) }
    }

    @Test
    fun `getUser returns user from repository`() {
        every { repository.getById("1") } returns alice
        assertEquals(alice, service.getUser("1"))
        verify(exactly = 1) { repository.getById("1") }
    }

    @Test
    fun `getUser returns null when repository returns null`() {
        every { repository.getById("99") } returns null
        assertNull(service.getUser("99"))
    }

    @Test
    fun `updateUser delegates to repository update`() {
        every { repository.update(alice) } returns Unit
        service.updateUser(alice)
        verify(exactly = 1) { repository.update(alice) }
    }

    @Test
    fun `deleteUser delegates to repository delete`() {
        every { repository.delete("1") } returns Unit
        service.deleteUser("1")
        verify(exactly = 1) { repository.delete("1") }
    }

    @Test
    fun `addFriend delegates to repository addFriend`() {
        every { repository.addFriend("1", "2") } returns Unit
        service.addFriend("1", "2")
        verify(exactly = 1) { repository.addFriend("1", "2") }
    }

    @Test
    fun `removeFriend delegates to repository removeFriend`() {
        every { repository.removeFriend("1", "2") } returns Unit
        service.removeFriend("1", "2")
        verify(exactly = 1) { repository.removeFriend("1", "2") }
    }

    @Test
    fun `getFriends returns list from repository`() {
        val friends = listOf(alice)
        every { repository.getFriends("1") } returns friends
        assertEquals(friends, service.getFriends("1"))
        verify(exactly = 1) { repository.getFriends("1") }
    }

    @Test
    fun `suggestFriends delegates to getFriendsOfFriends`() {
        val suggestions = listOf(alice)
        every { repository.getFriendsOfFriends("1") } returns suggestions
        assertEquals(suggestions, service.suggestFriends("1"))
        verify(exactly = 1) { repository.getFriendsOfFriends("1") }
    }
}
