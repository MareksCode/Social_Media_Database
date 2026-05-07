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
        profilePicture = null, friends = emptyList()
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
    fun `createUser sets profilePicture to null by default`() {
        val result = service.createUser("testUser", "testuser@example.com", Status.ONLINE, "clash royale", "IT", "007")
        assertNull(result.profilePicture)
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
    fun `change delegates String property to repository`() {
        service.change("1", UserExposedProperty.NAME, "Bob")
        verify { repository.updateProperty("1", UserExposedProperty.NAME, "Bob") }
    }

    @Test
    fun `change delegates Status property to repository`() {
        service.change("1", UserExposedProperty.STATUS, Status.BUSY)
        verify { repository.updateProperty("1", UserExposedProperty.STATUS, Status.BUSY) }
    }

    @Test
    fun `change throws IllegalArgumentException on type mismatch for NAME`() {
        assertThrows<IllegalArgumentException> { service.change("1", UserExposedProperty.NAME, 42) }
    }

    @Test
    fun `change throws IllegalArgumentException on type mismatch for STATUS`() {
        assertThrows<IllegalArgumentException> { service.change("1", UserExposedProperty.STATUS, "ONLINE") }
    }

    @Test
    fun `change allows null for PROFILE_PICTURE`() {
        service.change("1", UserExposedProperty.PROFILE_PICTURE, null)
        verify { repository.updateProperty("1", UserExposedProperty.PROFILE_PICTURE, null) }
    }

    @Test
    fun `change delegates non-null String to PROFILE_PICTURE`() {
        service.change("1", UserExposedProperty.PROFILE_PICTURE, "https://example.com/pic.jpg")
        verify { repository.updateProperty("1", UserExposedProperty.PROFILE_PICTURE, "https://example.com/pic.jpg") }
    }

    @Test
    fun `change throws IllegalArgumentException when null passed for non-nullable property`() {
        assertThrows<IllegalArgumentException> { service.change("1", UserExposedProperty.NAME, null) }
    }

    @Test
    fun `get delegates to repository`() {
        every { repository.getProperty("1", UserExposedProperty.NAME) } returns "Alice"
        assertEquals("Alice", service.get("1", UserExposedProperty.NAME))
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
