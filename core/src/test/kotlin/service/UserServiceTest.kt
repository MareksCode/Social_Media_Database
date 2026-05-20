package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import repository.UserRepository

class UserServiceTest {
    private val repository = mockk<UserRepository>(relaxed = true)
    private val service = UserService(repository)

    private val testUser = User(
        id = "1", name = "testUser", email = "testuser@example.com",
        status = Status.ONLINE, interest = "clash royale",
        department = "IT", room = "007",
        profilePicture = ""
    )

    @Test
    fun `createUser generates non-blank id and stores user`() {
        service.createUser("testUser", "testuser@example.com", Status.ONLINE, "clash royale", "IT", "007")
        verify { repository.create(match { it.id.isNotBlank() && it.name == "testUser" }) }
    }

    @Test
    fun `createUser returns user with generated id`() {
        val result = service.createUser("testUser", "testuser@example.com", Status.ONLINE, "clash royale", "IT", "007")
        assertTrue(result.id.isNotBlank())
        assertEquals("testUser", result.name)
    }

    @Test
    fun `createUser sets profilePicture to empty string by default`() {
        val result = service.createUser("testUser", "testuser@example.com", Status.ONLINE, "clash royale", "IT", "007")
        assertEquals("", result.profilePicture)
    }

    @Test
    fun `createUser generates unique ids`() {
        val a = service.createUser("A", "a@example.com", Status.ONLINE, "chess", "IT", "1")
        val b = service.createUser("B", "b@example.com", Status.ONLINE, "chess", "IT", "2")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `getUser returns user from repository`() {
        every { repository.getById("1") } returns testUser
        assertEquals(testUser, service.getUser("1"))
    }

    @Test
    fun `getUser returns null when not found`() {
        every { repository.getById("99") } returns null
        assertNull(service.getUser("99"))
    }

    @Test
    fun `deleteUser delegates to repository`() {
        service.deleteUser("1")
        verify { repository.delete("1") }
    }

    @Test
    fun `updateUser delegates to repository`() {
        val update = UserExposedProperty(name = "Bob")
        service.updateUser("1", update)
        verify { repository.updateUser("1", update) }
    }

    @Test
    fun `updateUser with multiple fields delegates to repository`() {
        val update = UserExposedProperty(name = "Bob", status = Status.BUSY)
        service.updateUser("1", update)
        verify { repository.updateUser("1", update) }
    }

    @Test
    fun `sendFriendRequest delegates to repository`() {
        service.sendFriendRequest("1", "2")
        verify { repository.sendFriendRequest("1", "2") }
    }

    @Test
    fun `sendFriendRequest throws when fromId equals toId`() {
        assertThrows<IllegalArgumentException> { service.sendFriendRequest("1", "1") }
    }

    @Test
    fun `getPendingFriendRequests delegates to repository`() {
        val requests = listOf(FriendRequest("2", "1"))
        every { repository.getPendingFriendRequests("1") } returns requests
        assertEquals(requests, service.getPendingFriendRequests("1"))
    }

    @Test
    fun `getFriends delegates to repository`() {
        every { repository.getFriends("1") } returns listOf(testUser)
        assertEquals(listOf(testUser), service.getFriends("1"))
    }

    @Test
    fun `suggestFriends delegates to getFriendsOfFriends`() {
        every { repository.getFriendsOfFriends("1") } returns listOf(testUser)
        assertEquals(listOf(testUser), service.suggestFriends("1"))
    }

    @Test
    fun `removeFriend delegates to repository`() {
        service.removeFriend("1", "2")
        verify { repository.removeFriend("1", "2") }
    }
}
