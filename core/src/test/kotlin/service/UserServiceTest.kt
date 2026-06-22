package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import model.FriendRequest
import model.Friendship
import model.Status
import model.User
import model.UserUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest {
    private val repository = mockk<UserRepository>(relaxed = true) // relaxed: unstubbed calls return defaults instead of throwing
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC) // fixed clock: time never advances, tests are deterministic
    private val service = UserService(repository, clock)

    private fun user(id: String, name: String = "User$id") = User(
        id = id, name = name, email = "$id@example.com",
        status = Status.ONLINE, interest = "clash royale",
        department = "IT", room = "007",
        profilePicture = ""
    )

    private val testUser = user("1", "testUser")

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
        val update = UserUpdate(name = "Bob")
        service.updateUser("1", update)
        verify { repository.update("1", update) }
    }

    @Test
    fun `updateUser with multiple fields delegates to repository`() {
        val update = UserUpdate(name = "Bob", status = Status.BUSY)
        service.updateUser("1", update)
        verify { repository.update("1", update) }
    }

    @Test
    fun `sendFriendRequest with no reverse request stores a request with clock time`() {
        every { repository.friendRequestExists("2", "1") } returns false
        service.sendFriendRequest("1", "2")
        verify { repository.addFriendRequest("1", "2", now) }
    }

    @Test
    fun `sendFriendRequest with reverse request pending creates friendship`() {
        every { repository.friendRequestExists("2", "1") } returns true
        service.sendFriendRequest("1", "2")
        verify { repository.acceptFriendRequest("1", "2", now) }
    }

    @Test
    fun `sendFriendRequest throws when fromId equals toId`() {
        assertThrows<IllegalArgumentException> { service.sendFriendRequest("1", "1") }
    }

    @Test
    fun `sendFriendRequest throws when users are already friends`() {
        every { repository.areFriends("1", "2") } returns true
        assertThrows<IllegalArgumentException> { service.sendFriendRequest("1", "2") }
    }

    @Test
    fun `declineFriendRequest removes the incoming request`() {
        service.declineFriendRequest("1", "2")
        verify { repository.removeFriendRequest("2", "1") }
    }

    @Test
    fun `getPendingFriendRequests delegates to getIncomingFriendRequests`() {
        val requests = listOf(FriendRequest("2", "1", now))
        every { repository.getIncomingFriendRequests("1") } returns requests
        assertEquals(requests, service.getPendingFriendRequests("1"))
    }

    @Test
    fun `getFriends delegates to repository`() {
        val friendships = listOf(Friendship("1", "2", now))
        every { repository.getFriends("1") } returns friendships
        assertEquals(friendships, service.getFriends("1"))
    }

    @Test
    fun `getFriendRecommendations excludes self and direct friends and ranks by shared count`() {
        // D has 2 common friends, E has 1
        every { repository.getFriends("A") } returns listOf(
            Friendship("A", "B", now), Friendship("A", "C", now)
        )
        every { repository.getFriendsOf(setOf("B", "C")) } returns mapOf(
            "B" to listOf(Friendship("B", "A", now), Friendship("B", "D", now), Friendship("B", "E", now)),
            "C" to listOf(Friendship("C", "A", now), Friendship("C", "D", now), Friendship("C", "B", now))
        )
        every { repository.getById("D") } returns user("D")
        every { repository.getById("E") } returns user("E")
        val recommendations = service.getFriendRecommendations("A").map { it.id }
        assertEquals(listOf("D", "E"), recommendations)
    }

    @Test
    fun `getFriendRecommendations returns empty when user has no friends`() {
        every { repository.getFriends("A") } returns emptyList()
        every { repository.getFriendsOf(emptySet()) } returns emptyMap()
        assertTrue(service.getFriendRecommendations("A").isEmpty())
    }

    @Test
    fun `removeFriend delegates to repository`() {
        service.removeFriend("1", "2")
        verify { repository.removeFriend("1", "2") }
    }
}
