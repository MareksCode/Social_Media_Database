package repository

import model.Status
import model.User
import model.UserUpdate
import org.junit.jupiter.api.AfterAll
import service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

// PER_CLASS: one shared test instance, so @BeforeAll/@AfterAll can be non-static methods
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jUserRepositoryIT {
    private lateinit var driver: org.neo4j.driver.Driver
    private lateinit var repository: Neo4jUserRepository
    private lateinit var userService: UserService

    private val fixedNow = Instant.parse("2026-06-10T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @BeforeAll
    fun start() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "passwort"))
        repository = Neo4jUserRepository(driver)
        repository.ensureSchema()
        userService = UserService(repository, clock)
    }

    @AfterAll
    fun stop() {
        clearDatabase()
        repository.close()
        driver.close()
    }

    @BeforeEach
    fun clearDatabase() {
        // wipe all nodes and relationships before each test for isolation
        driver.session().use { it.run("MATCH (n) DETACH DELETE n") }
    }

    private fun user(id: String, name: String = "User$id") = User(
        id = id, name = name, email = "$id@example.com",
        status = Status.ONLINE, interest = "testing",
        department = "IT", room = "101",
        profilePicture = ""
    )

    private fun makeFriends(a: String, b: String) {
        repository.addFriend(a, b, fixedNow)
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

    // --- update ---

    @Test
    fun `update changes name`() {
        repository.create(user("1", "Alice"))
        repository.update("1", UserUpdate(name = "Alice Updated"))
        assertEquals("Alice Updated", repository.getById("1")!!.name)
    }

    @Test
    fun `update changes status`() {
        repository.create(user("1", "Alice"))
        repository.update("1", UserUpdate(status = Status.BUSY))
        assertEquals(Status.BUSY, repository.getById("1")!!.status)
    }

    @Test
    fun `update changes multiple fields`() {
        repository.create(user("1", "Alice"))
        repository.update("1", UserUpdate(name = "Alice Updated", department = "HR"))
        val updated = repository.getById("1")!!
        assertEquals("Alice Updated", updated.name)
        assertEquals("HR", updated.department)
    }

    @Test
    fun `update does not change unset fields`() {
        repository.create(user("1", "Alice"))
        repository.update("1", UserUpdate(name = "Alice Updated"))
        val updated = repository.getById("1")!!
        assertEquals("1@example.com", updated.email)
        assertEquals(Status.ONLINE, updated.status)
    }

    @Test
    fun `update with empty object changes nothing`() {
        val u = user("1", "Alice")
        repository.create(u)
        repository.update("1", UserUpdate())
        assertEquals(u, repository.getById("1"))
    }

    // --- friend requests (primitives) ---

    @Test
    fun `addFriendRequest stores incoming request with sendTime`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriendRequest("1", "2", fixedNow)
        val incoming = repository.getIncomingFriendRequests("2")
        assertEquals(1, incoming.size)
        assertEquals("1", incoming[0].fromId)
        assertEquals("2", incoming[0].toId)
        assertEquals(fixedNow.toEpochMilli(), incoming[0].sendTime.toEpochMilli())
    }

    @Test
    fun `addFriendRequest is idempotent`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriendRequest("1", "2", fixedNow)
        repository.addFriendRequest("1", "2", fixedNow)
        assertEquals(1, repository.getIncomingFriendRequests("2").size)
    }

    @Test
    fun `removeFriendRequest deletes the request`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriendRequest("1", "2", fixedNow)
        repository.removeFriendRequest("1", "2")
        assertTrue(repository.getIncomingFriendRequests("2").isEmpty())
    }

    @Test
    fun `friendRequestExists reflects presence of request`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        assertTrue(!repository.friendRequestExists("1", "2"))
        repository.addFriendRequest("1", "2", fixedNow)
        assertTrue(repository.friendRequestExists("1", "2"))
        assertTrue(!repository.friendRequestExists("2", "1"))
    }

    @Test
    fun `getIncomingFriendRequests returns only requests sent to userId`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        repository.addFriendRequest("2", "1", fixedNow)
        repository.addFriendRequest("3", "1", fixedNow)
        val incoming = repository.getIncomingFriendRequests("1")
        assertEquals(2, incoming.size)
        assertTrue(incoming.all { it.toId == "1" })
    }

    @Test
    fun `getIncomingFriendRequests does not return outgoing requests`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriendRequest("1", "2", fixedNow)
        assertTrue(repository.getIncomingFriendRequests("1").isEmpty())
    }

    // --- friend requests (service flow) ---

    @Test
    fun `sendFriendRequest one-sided does not create friend relation`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        userService.sendFriendRequest("1", "2")
        assertTrue(repository.getFriends("1").isEmpty())
        assertTrue(repository.getFriends("2").isEmpty())
    }

    @Test
    fun `sendFriendRequest mutual send creates friendship and clears requests`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        userService.sendFriendRequest("1", "2")
        userService.sendFriendRequest("2", "1")
        assertTrue("2" in repository.getFriends("1").map { it.other("1") })
        assertTrue("1" in repository.getFriends("2").map { it.other("2") })
        assertTrue(repository.getIncomingFriendRequests("1").isEmpty())
        assertTrue(repository.getIncomingFriendRequests("2").isEmpty())
    }

    @Test
    fun `declineFriendRequest removes the pending request`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        userService.sendFriendRequest("1", "2")
        userService.declineFriendRequest("2", "1")
        assertTrue(repository.getIncomingFriendRequests("2").isEmpty())
        assertTrue(repository.getFriends("1").isEmpty())
    }

    // --- addFriend / getFriends ---

    @Test
    fun `getFriends returns empty list for user with no friends`() {
        repository.create(user("1", "Alice"))
        assertTrue(repository.getFriends("1").isEmpty())
    }

    @Test
    fun `addFriend creates a single friendship queryable from both sides`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriend("1", "2", fixedNow)
        val friendship = repository.getFriends("1").single()
        assertEquals("2", friendship.other("1"))
        assertEquals(fixedNow.toEpochMilli(), friendship.createTime.toEpochMilli())
        assertEquals("1", repository.getFriends("2").single().other("2"))
    }

    @Test
    fun `getFriends does not include non-friends`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.create(user("3", "Carol"))
        makeFriends("1", "2")
        val friendIds = repository.getFriends("1").map { it.other("1") }
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
        val friendIds = repository.getFriends("1").map { it.other("1") }
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
        val friendIds = repository.getFriends("1").map { it.other("1") }
        assertTrue("2" !in friendIds)
        assertTrue("3" in friendIds)
    }

    // --- getFriendsOf (batched primitive) ---

    @Test
    fun `getFriendsOf returns friends of all given users keyed by user id`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        repository.create(user("D"))
        makeFriends("A", "B")
        makeFriends("A", "C")
        makeFriends("B", "D")
        makeFriends("C", "D")
        // B -> {A,D}, C -> {A,D}
        val result = repository.getFriendsOf(listOf("B", "C"))
        assertEquals(setOf("B", "C"), result.keys)
        assertEquals(listOf("A", "D"), result.getValue("B").map { it.other("B") }.sorted())
        assertEquals(listOf("A", "D"), result.getValue("C").map { it.other("C") }.sorted())
    }

    @Test
    fun `getFriendsOf returns empty for empty input`() {
        assertTrue(repository.getFriendsOf(emptyList()).isEmpty())
    }

    // --- getFriendRecommendations (service) ---

    @Test
    fun `getFriendRecommendations returns empty when no 2-hop connections`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        makeFriends("1", "2")
        assertTrue(userService.getFriendRecommendations("1").isEmpty())
    }

    @Test
    fun `getFriendRecommendations returns 2-hop users excluding direct friends and self`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        makeFriends("A", "B")
        makeFriends("B", "C")
        val fof = userService.getFriendRecommendations("A").map { it.id }
        assertTrue("C" in fof)
        assertTrue("A" !in fof)
        assertTrue("B" !in fof)
    }

    @Test
    fun `getFriendRecommendations orders by common friend count descending`() {
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
        val fof = userService.getFriendRecommendations("A").map { it.id }
        assertEquals("D", fof[0])
        assertEquals("E", fof[1])
    }
}
