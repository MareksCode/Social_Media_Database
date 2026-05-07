package repository

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jUserRepositoryIT {
    private lateinit var driver: org.neo4j.driver.Driver
    private lateinit var repository: Neo4jUserRepository

    @BeforeAll
    fun start() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "passwort"))
        repository = Neo4jUserRepository(driver)
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

    private fun user(id: String, name: String = "User$id") = User(
        id = id, name = name, email = "$id@example.com",
        status = Status.ONLINE, interest = "testing",
        department = "IT", room = "101",
        profilePicture = null, friends = emptyList()
    )

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

    @Test
    fun `delete removes node`() {
        repository.create(user("1"))
        repository.delete("1")
        assertNull(repository.getById("1"))
    }

    @Test
    fun `null profilePicture returns as null`() {
        repository.create(user("1"))
        assertNull(repository.getById("1")!!.profilePicture)
    }

    @Test
    fun `non-null profilePicture returns correctly`() {
        val u = user("1").copy(profilePicture = "https://example.com/pic.jpg")
        repository.create(u)
        assertEquals("https://example.com/pic.jpg", repository.getById("1")!!.profilePicture)
    }

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

    @Test
    fun `getProperty returns name`() {
        repository.create(user("1", "Alice"))
        assertEquals("Alice", repository.getProperty("1", UserExposedProperty.NAME))
    }

    @Test
    fun `getProperty returns status as Status enum`() {
        repository.create(user("1", "Alice"))
        assertEquals(Status.ONLINE, repository.getProperty("1", UserExposedProperty.STATUS))
    }

    @Test
    fun `getProperty returns null profilePicture`() {
        repository.create(user("1"))
        assertNull(repository.getProperty("1", UserExposedProperty.PROFILE_PICTURE))
    }

    @Test
    fun `getProperty returns null for unknown user`() {
        assertNull(repository.getProperty("missing", UserExposedProperty.NAME))
    }

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
    fun `sendFriendRequest mutual send creates friendship`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        repository.sendFriendRequest("2", "1")
        assertTrue("2" in repository.getFriends("1").map { it.id })
        assertTrue("1" in repository.getFriends("2").map { it.id })
    }

    @Test
    fun `sendFriendRequest mutual send removes pending requests`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        repository.sendFriendRequest("2", "1")
        assertTrue(repository.getPendingFriendRequests("1").isEmpty())
        assertTrue(repository.getPendingFriendRequests("2").isEmpty())
    }

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

    @Test
    fun `removeFriend removes relationship on both sides`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.sendFriendRequest("1", "2")
        repository.sendFriendRequest("2", "1")
        repository.removeFriend("1", "2")
        assertTrue(repository.getFriends("1").isEmpty())
        assertTrue(repository.getFriends("2").isEmpty())
    }

    @Test
    fun `getFriendsOfFriends returns 2-hop users excluding direct friends and self`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        repository.sendFriendRequest("A", "B"); repository.sendFriendRequest("B", "A")
        repository.sendFriendRequest("B", "C"); repository.sendFriendRequest("C", "B")
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
        repository.sendFriendRequest("A", "B"); repository.sendFriendRequest("B", "A")
        repository.sendFriendRequest("A", "C"); repository.sendFriendRequest("C", "A")
        repository.sendFriendRequest("B", "D"); repository.sendFriendRequest("D", "B")
        repository.sendFriendRequest("C", "D"); repository.sendFriendRequest("D", "C")
        repository.sendFriendRequest("B", "E"); repository.sendFriendRequest("E", "B")
        val fof = repository.getFriendsOfFriends("A").map { it.id }
        assertEquals("D", fof[0])
        assertEquals("E", fof[1])
    }
}
