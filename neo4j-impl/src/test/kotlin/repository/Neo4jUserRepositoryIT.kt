package repository

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import org.junit.jupiter.api.AfterAll
import service.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jUserRepositoryIT {
    private val waitBetweenTests = false

    private lateinit var driver: org.neo4j.driver.Driver
    private lateinit var repository: Neo4jUserRepository
    private lateinit var userService: UserService

    @BeforeAll
    fun start() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "passwort"))
        repository = Neo4jUserRepository(driver)
        userService = UserService(repository)
    }

    @AfterAll
    fun stop() {
        repository.close()
        driver.close()
    }

    @BeforeEach
    fun clearDatabase() {
        driver.session().use { it.run("MATCH (n) DETACH DELETE n") }
    }

    @AfterEach
    fun waitForInput(testInfo: TestInfo) {
        if (waitBetweenTests) {
            val frame = javax.swing.JFrame().apply { isAlwaysOnTop = true; isVisible = true }
            javax.swing.JOptionPane.showMessageDialog(frame, "Finished: ${testInfo.displayName}\n\nPress OK to run next test")
            frame.dispose()
        }
    }

    private fun user(id: String, name: String = "User$id") = User(
        id = id, name = name, email = "$id@example.com",
        status = Status.ONLINE, interest = "testing",
        department = "IT", room = "101",
        profilePicture = ""
    )

    private fun makeFriends(a: String, b: String) {
        repository.sendFriendRequest(a, b)
        repository.sendFriendRequest(b, a)
    }

    // --- create / getById ---

    @Test
    fun `create and getById`() {
        val u = user("1", "Alice")
        repository.create(u)
        assertEquals(u, repository.getById("1"))
    }

    @Test
    fun `getById returns null for unknown id`() {
        assertNull(repository.getById("missing"))
    }

    // --- delete ---

    @Test
    fun `delete removes node`() {
        repository.create(user("1"))
        repository.delete("1")
        assertNull(repository.getById("1"))
    }

    // --- profilePicture ---

    @Test
    fun `non-null profilePicture returns correctly`() {
        val u = user("1").copy(profilePicture = "https://example.com/pic.jpg")
        repository.create(u)
        assertEquals("https://example.com/pic.jpg", repository.getById("1")!!.profilePicture)
    }

    // --- updateProperty ---

    @Test
    fun `updateProperty changes name`() {
        repository.create(user("1", "Alice"))
        repository.updateProperty("1", UserExposedProperty.NAME, "Alice Updated")
        assertEquals("Alice Updated", repository.getById("1")!!.name)
    }

    @Test
    fun `updateProperty changes status`() {
        repository.create(user("1", "Alice"))
        repository.updateProperty("1", UserExposedProperty.STATUS, Status.BUSY)
        assertEquals(Status.BUSY, repository.getById("1")!!.status)
    }

    @Test
    fun `updateProperty sets profilePicture to null`() {
        val u = user("1").copy(profilePicture = "https://example.com/pic.jpg")
        repository.create(u)
        repository.updateProperty("1", UserExposedProperty.PROFILE_PICTURE, null)
        assertNull(repository.getById("1")!!.profilePicture)
    }

    // --- sendFriendRequest ---

    @Test
    fun `sendFriendRequest stores pending request`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        val pending = repository.getPendingFriendRequests("2")
        assertEquals(1, pending.size)
        assertEquals("1", pending[0].fromId)
        assertEquals("2", pending[0].toId)
    }

    @Test
    fun `sendFriendRequest one-sided does not create friend relation`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        assertTrue(repository.getFriends("1").isEmpty())
        assertTrue(repository.getFriends("2").isEmpty())
    }

    @Test
    fun `sendFriendRequest mutual send creates friendship`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        assertTrue("2" in repository.getFriends("1").map { it.id })
        assertTrue("1" in repository.getFriends("2").map { it.id })
    }

    @Test
    fun `sendFriendRequest mutual send removes pending requests`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        assertTrue(repository.getPendingFriendRequests("1").isEmpty())
        assertTrue(repository.getPendingFriendRequests("2").isEmpty())
    }

    @Test
    fun `sendFriendRequest duplicate does not create duplicate pending request`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        repository.sendFriendRequest("1", "2")
        assertEquals(1, repository.getPendingFriendRequests("2").size)
    }

    // --- getPendingFriendRequests ---

    @Test
    fun `getPendingFriendRequests returns only requests sent to userId`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        repository.sendFriendRequest("2", "1")
        repository.sendFriendRequest("3", "1")
        val pending = repository.getPendingFriendRequests("1")
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.toId == "1" })
    }

    @Test
    fun `getPendingFriendRequests does not return sent requests`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        assertTrue(repository.getPendingFriendRequests("1").isEmpty())
    }

    // --- getFriends ---

    @Test
    fun `getFriends returns empty list for user with no friends`() {
        repository.create(user("1", "Alice"))
        assertTrue(repository.getFriends("1").isEmpty())
    }

    @Test
    fun `getFriends returns correct user data`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        val friends = repository.getFriends("1")
        assertEquals(1, friends.size)
        assertEquals("2", friends[0].id)
        assertEquals("Bob", friends[0].name)
        assertEquals("2@example.com", friends[0].email)
    }

    @Test
    fun `getFriends does not include non-friends`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        makeFriends("1", "2")
        val friendIds = repository.getFriends("1").map { it.id }
        assertTrue("2" in friendIds)
        assertTrue("3" !in friendIds)
    }

    @Test
    fun `getFriends returns multiple friends`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        makeFriends("1", "2")
        makeFriends("1", "3")
        val friendIds = repository.getFriends("1").map { it.id }
        assertEquals(2, friendIds.size)
        assertTrue("2" in friendIds)
        assertTrue("3" in friendIds)
    }

    // --- removeFriend ---

    @Test
    fun `removeFriend removes relationship on both sides`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        repository.removeFriend("1", "2")
        assertTrue(repository.getFriends("1").isEmpty())
        assertTrue(repository.getFriends("2").isEmpty())
    }

    @Test
    fun `removeFriend keeps other friendships intact`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        makeFriends("1", "2")
        makeFriends("1", "3")
        repository.removeFriend("1", "2")
        val friendIds = repository.getFriends("1").map { it.id }
        assertTrue("2" !in friendIds)
        assertTrue("3" in friendIds)
    }

    // --- getFriendsOfFriends ---

    @Test
    fun `getFriendsOfFriends returns empty when no 2-hop connections`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        assertTrue(repository.getFriendsOfFriends("1").isEmpty())
    }

    @Test
    fun `getFriendsOfFriends returns 2-hop users excluding direct friends and self`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        makeFriends("A", "B")
        makeFriends("B", "C")
        val fof = repository.getFriendsOfFriends("A").map { it.id }
        assertTrue("C" in fof)
        assertTrue("A" !in fof)
        assertTrue("B" !in fof)
    }

    @Test
    fun `getFriendsOfFriends orders by common friend count descending`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        repository.create(user("D"))
        repository.create(user("E"))
        makeFriends("A", "B")
        makeFriends("A", "C")
        makeFriends("B", "D")
        makeFriends("C", "D")
        makeFriends("B", "E")
        val fof = repository.getFriendsOfFriends("A").map { it.id }
        assertEquals("D", fof[0])
        assertEquals("E", fof[1])
    }
}
