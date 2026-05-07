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

    private val testUser = User(
        id = "1", 
        name = "testUser", 
        email = "testuser@example.com",
        status = Status.ONLINE, 
        interest = "clash royale",
        department = "IT", 
        room = "007", 
        profilePicture = null,
        friends = emptyList()
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        service = UserService(repository)
    }

    @Test
    fun `createUser uses repository create & does it once`() {
        every { repository.create(testUser) } returns Unit
        service.createUser(testUser)
        verify(exactly = 1) { repository.create(testUser) }
    }

    @Test
    fun `getUser returns user from repository`() {
        every { repository.getById("1") } returns testUser
        assertEquals(testUser, service.getUser("1"))
        verify(exactly = 1) { repository.getById("1") }
    }

    @Test
    fun `getUser returns null when repository returns null`() {
        every { repository.getById("99") } returns null
        assertNull(service.getUser("99"))
    }

    @Test
    fun `deleteUser uses repository delete & does it once`() {
        every { repository.delete("1") } returns Unit
        service.deleteUser("1")
        verify(exactly = 1) { repository.delete("1") }
    }

    @Test
    fun `removeFriend uses repository removeFriend & does it once`() {
        every { repository.removeFriend("1", "2") } returns Unit
        service.removeFriend("1", "2")
        verify(exactly = 1) { repository.removeFriend("1", "2") }
    }

    @Test
    fun `getFriends returns list from repository`() {
        val friends = listOf(testUser)
        every { repository.getFriends("1") } returns friends
        assertEquals(friends, service.getFriends("1"))
        verify(exactly = 1) { repository.getFriends("1") }
    }

    @Test
    fun `suggestFriends uses getFriendsOfFriends & does it once`() {
        val suggestions = listOf(testUser)
        every { repository.getFriendsOfFriends("1") } returns suggestions
        assertEquals(suggestions, service.suggestFriends("1"))
        verify(exactly = 1) { repository.getFriendsOfFriends("1") }
    }
}
