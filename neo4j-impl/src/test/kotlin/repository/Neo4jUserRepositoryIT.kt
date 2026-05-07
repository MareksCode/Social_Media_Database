package repository

import model.Status
import model.User
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
