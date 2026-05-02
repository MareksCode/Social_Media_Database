package repository

import model.Status
import model.User
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.GraphDatabase
import org.testcontainers.containers.Neo4jContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jUserRepositoryIT {

    private val container = Neo4jContainer<Nothing>("neo4j:5").apply {
        withoutAuthentication()
    }

    private lateinit var driver: org.neo4j.driver.Driver
    private lateinit var repository: Neo4jUserRepository

    @BeforeAll
    fun startContainer() {
        container.start()
        driver = GraphDatabase.driver(container.boltUrl)
        repository = Neo4jUserRepository(driver)
    }

    @AfterAll
    fun stopContainer() {
        repository.close()
        container.stop()
    }

    @BeforeEach
    fun clearDatabase() {
        driver.session().use { it.run("MATCH (n) DETACH DELETE n") }
    }

    private fun user(id: String, name: String = "User$id") = User(
        id = id, name = name, email = "$id@example.com",
        status = Status.ONLINE, interest = "testing",
        abteilung = "IT", raum = "101", profilbild = null,
        friends = emptyList()
    )

    @Test
    fun `create and getById roundtrip`() {
        val u = user("1", "Alice")
        repository.create(u)
        assertEquals(u, repository.getById("1"))
    }

    @Test
    fun `getById returns null for unknown id`() {
        assertNull(repository.getById("missing"))
    }

    @Test
    fun `update changes fields`() {
        repository.create(user("1", "Alice"))
        val updated = user("1", "Alice Updated").copy(status = Status.BUSY, raum = "999")
        repository.update(updated)
        assertEquals(updated, repository.getById("1"))
    }

    @Test
    fun `delete removes node`() {
        repository.create(user("1"))
        repository.delete("1")
        assertNull(repository.getById("1"))
    }

    @Test
    fun `addFriend creates symmetric relationship`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriend("1", "2")
        assertTrue("2" in repository.getFriends("1").map { it.id })
        assertTrue("1" in repository.getFriends("2").map { it.id })
    }

    @Test
    fun `addFriend updates friends property on both nodes`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriend("1", "2")
        assertTrue("2" in repository.getById("1")!!.friends)
        assertTrue("1" in repository.getById("2")!!.friends)
    }

    @Test
    fun `removeFriend removes relationship and property on both nodes`() {
        repository.create(user("1", "Alice"))
        repository.create(user("2", "Bob"))
        repository.addFriend("1", "2")
        repository.removeFriend("1", "2")
        assertTrue(repository.getFriends("1").isEmpty())
        assertTrue(repository.getFriends("2").isEmpty())
        assertTrue(repository.getById("1")!!.friends.isEmpty())
        assertTrue(repository.getById("2")!!.friends.isEmpty())
    }

    @Test
    fun `getFriendsOfFriends returns 2-hop users excluding direct friends and self`() {
        repository.create(user("A"))
        repository.create(user("B"))
        repository.create(user("C"))
        repository.addFriend("A", "B")
        repository.addFriend("B", "C")
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
        repository.addFriend("A", "B")
        repository.addFriend("A", "C")
        repository.addFriend("B", "D")
        repository.addFriend("C", "D")
        repository.addFriend("B", "E")
        val fof = repository.getFriendsOfFriends("A").map { it.id }
        assertEquals("D", fof[0])
        assertEquals("E", fof[1])
    }

    @Test
    fun `null profilbild roundtrips as null`() {
        repository.create(user("1"))
        assertNull(repository.getById("1")!!.profilbild)
    }

    @Test
    fun `non-null profilbild roundtrips correctly`() {
        val u = user("1").copy(profilbild = "https://example.com/pic.jpg")
        repository.create(u)
        assertEquals("https://example.com/pic.jpg", repository.getById("1")!!.profilbild)
    }
}
