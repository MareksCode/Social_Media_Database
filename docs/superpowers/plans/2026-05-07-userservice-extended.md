# UserService Extended Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend UserService to enforce system-assigned IDs, typed property access via `UserExposedProperty` enum, and a friend-request system where mutual sends auto-resolve to friendship in a single atomic Neo4j transaction.

**Architecture:** Two new model classes (`FriendRequest`, `UserExposedProperty`) are added to core. `UserRepository` interface is updated — removing `update`/`addFriend`, adding `updateProperty`/`getProperty`/`sendFriendRequest`/`getPendingFriendRequests`. `UserService` is rewritten to generate UUIDs on creation and validate property types at runtime. `Neo4jUserRepository` gains new Cypher queries including an atomic `CALL {}` subquery for mutual-send detection.

**Tech Stack:** Kotlin, Neo4j Java Driver 5.26.0, JUnit 5, MockK 1.13, Maven multi-module

---

## File Map

| File | Action |
|------|--------|
| `core/src/main/kotlin/model/FriendRequest.kt` | Create |
| `core/src/main/kotlin/model/UserExposedProperty.kt` | Create |
| `core/src/main/kotlin/repository/UserRepository.kt` | Rewrite |
| `core/src/main/kotlin/service/UserService.kt` | Rewrite |
| `core/src/test/kotlin/service/UserServiceTest.kt` | Rewrite |
| `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt` | Rewrite |
| `neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt` | Rewrite |

---

### Task 1: Add FriendRequest and UserExposedProperty models

**Files:**
- Create: `core/src/main/kotlin/model/FriendRequest.kt`
- Create: `core/src/main/kotlin/model/UserExposedProperty.kt`

- [ ] **Step 1: Create FriendRequest.kt**

```kotlin
package model

data class FriendRequest(
    val fromId: String,
    val toId: String
)
```

- [ ] **Step 2: Create UserExposedProperty.kt**

```kotlin
package model

enum class UserExposedProperty {
    NAME, EMAIL, STATUS, INTEREST, DEPARTMENT, ROOM, PROFILE_PICTURE
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl core`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/model/FriendRequest.kt core/src/main/kotlin/model/UserExposedProperty.kt
git commit -m "feat: add FriendRequest and UserExposedProperty models"
```

---

### Task 2: Update UserRepository interface + stub Neo4jUserRepository + fix IT tests

This task makes a breaking interface change and restores compilation across the whole project. All three files must change together.

**Files:**
- Rewrite: `core/src/main/kotlin/repository/UserRepository.kt`
- Rewrite: `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt`
- Rewrite: `neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt`

- [ ] **Step 1: Rewrite UserRepository.kt**

`update` and `addFriend` are removed. New methods are added.

```kotlin
package repository

import model.FriendRequest
import model.User
import model.UserExposedProperty

interface UserRepository {
    fun create(user: User)
    fun getById(id: String): User?
    fun delete(id: String)
    fun updateProperty(id: String, property: UserExposedProperty, value: Any?)
    fun getProperty(id: String, property: UserExposedProperty): Any?
    fun sendFriendRequest(fromId: String, toId: String)
    fun getPendingFriendRequests(userId: String): List<FriendRequest>
    fun getFriends(userId: String): List<User>
    fun removeFriend(userId: String, friendId: String)
    fun getFriendsOfFriends(userId: String): List<User>
}
```

- [ ] **Step 2: Rewrite Neo4jUserRepository.kt**

`update` and `addFriend` are removed. New methods are stubbed with `TODO()` — they are implemented in Tasks 4 and 5. `toNeo4jKey()` extension is added.

```kotlin
package repository

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Values.parameters
import org.neo4j.driver.types.Node

class Neo4jUserRepository(private val driver: Driver) : UserRepository {
    companion object {
        fun connect(uri: String, user: String, password: String): Neo4jUserRepository =
            Neo4jUserRepository(GraphDatabase.driver(uri, AuthTokens.basic(user, password)))
    }

    private fun UserExposedProperty.toNeo4jKey(): String = when (this) {
        UserExposedProperty.NAME -> "name"
        UserExposedProperty.EMAIL -> "email"
        UserExposedProperty.STATUS -> "status"
        UserExposedProperty.INTEREST -> "interest"
        UserExposedProperty.DEPARTMENT -> "department"
        UserExposedProperty.ROOM -> "room"
        UserExposedProperty.PROFILE_PICTURE -> "profilePicture"
    }

    private fun nodeToUser(node: Node, friendIds: List<String> = emptyList()): User = User(
        id = node["id"].asString(),
        name = node["name"].asString(),
        email = node["email"].asString(),
        status = Status.entries.firstOrNull { it.name == node["status"].asString() } ?: Status.OFFLINE,
        interest = node["interest"].asString(),
        department = node["department"].asString(),
        room = node["room"].asString(),
        profilePicture = if (node["profilePicture"].isNull) null else node["profilePicture"].asString(),
        friends = friendIds
    )

    override fun create(user: User) {
        driver.session().use { session ->
            session.run(
                """CREATE (u:User {
                    id: ${'$'}id,
                    name: ${'$'}name,
                    email: ${'$'}email,
                    status: ${'$'}status,
                    interest: ${'$'}interest,
                    department: ${'$'}department,
                    room: ${'$'}room,
                    profilePicture: ${'$'}profilePicture
                })""",
                parameters(
                    "id", user.id,
                    "name", user.name,
                    "email", user.email,
                    "status", user.status.name,
                    "interest", user.interest,
                    "department", user.department,
                    "room", user.room,
                    "profilePicture", user.profilePicture
                )
            )
        }
    }

    override fun getById(id: String): User? {
        driver.session().use { session ->
            val result = session.run(
                """MATCH (u:User {id: ${'$'}id})
                   OPTIONAL MATCH (u)-[:FRIENDS_WITH]->(f:User)
                   RETURN u, collect(f.id) AS friendIds""",
                parameters("id", id)
            )
            val records = result.list()
            if (records.isEmpty()) return null
            val record = records[0]
            return nodeToUser(record["u"].asNode(), record["friendIds"].asList { it.asString() })
        }
    }

    override fun delete(id: String) {
        driver.session().use { session ->
            session.run(
                "MATCH (u:User {id: ${'$'}id}) DETACH DELETE u",
                parameters("id", id)
            )
        }
    }

    override fun updateProperty(id: String, property: UserExposedProperty, value: Any?) {
        TODO("not yet implemented")
    }

    override fun getProperty(id: String, property: UserExposedProperty): Any? {
        TODO("not yet implemented")
    }

    override fun sendFriendRequest(fromId: String, toId: String) {
        TODO("not yet implemented")
    }

    override fun getPendingFriendRequests(userId: String): List<FriendRequest> {
        TODO("not yet implemented")
    }

    override fun getFriends(userId: String): List<User> {
        driver.session().use { session ->
            val result = session.run(
                """MATCH (u:User {id: ${'$'}id})-[:FRIENDS_WITH]->(f:User)
                   OPTIONAL MATCH (f)-[:FRIENDS_WITH]->(ff:User)
                   RETURN f, collect(ff.id) AS friendIds""",
                parameters("id", userId)
            )
            return result.list { record ->
                nodeToUser(record["f"].asNode(), record["friendIds"].asList { it.asString() })
            }
        }
    }

    override fun removeFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            session.run(
                """MATCH (a:User {id: ${'$'}userId})-[r1:FRIENDS_WITH]->(b:User {id: ${'$'}friendId})
                   OPTIONAL MATCH (b)-[r2:FRIENDS_WITH]->(a)
                   DELETE r1, r2""",
                parameters("userId", userId, "friendId", friendId)
            )
        }
    }

    override fun getFriendsOfFriends(userId: String): List<User> {
        driver.session().use { session ->
            val queryResult = session.run(
                """MATCH (targetUser:User {id: ${'$'}id})-[:FRIENDS_WITH]->(directFriend)-[:FRIENDS_WITH]->(friendOfFriend)
                   WHERE NOT (targetUser)-[:FRIENDS_WITH]->(friendOfFriend) AND friendOfFriend <> targetUser
                   WITH friendOfFriend, count(directFriend) AS mutualFriendCount
                   OPTIONAL MATCH (friendOfFriend)-[:FRIENDS_WITH]->(friendOfFriendContact:User)
                   WITH friendOfFriend, mutualFriendCount, collect(friendOfFriendContact.id) AS friendOfFriendContactIds
                   ORDER BY mutualFriendCount DESC
                   RETURN friendOfFriend, friendOfFriendContactIds""",
                parameters("id", userId)
            )
            return queryResult.list { record ->
                nodeToUser(record["friendOfFriend"].asNode(), record["friendOfFriendContactIds"].asList { it.asString() })
            }
        }
    }

    fun close() = driver.close()
}
```

- [ ] **Step 3: Rewrite Neo4jUserRepositoryIT.kt**

Tests for `update` and `addFriend` are removed. Tests for `removeFriend` and `getFriendsOfFriends` are removed here and re-added in Task 5 once `sendFriendRequest` is implemented (their setup depended on `addFriend`).

```kotlin
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
```

- [ ] **Step 4: Verify project compiles**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run surviving IT tests**

Run: `mvn test -pl neo4j-impl`
Expected: `BUILD SUCCESS` — 5 tests pass

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/repository/UserRepository.kt neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt
git commit -m "refactor: update UserRepository interface; stub new Neo4j methods"
```

---

### Task 3: TDD UserService

**Files:**
- Rewrite: `core/src/test/kotlin/service/UserServiceTest.kt`
- Rewrite: `core/src/main/kotlin/service/UserService.kt`

- [ ] **Step 1: Rewrite UserServiceTest.kt**

Write the new tests first. Compilation will fail until Step 2 rewrites `UserService`.

```kotlin
package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        assert(result.id.isNotBlank())
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
        assert(a.id != b.id)
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
```

- [ ] **Step 2: Rewrite UserService.kt**

```kotlin
package service

import model.FriendRequest
import model.Status
import model.User
import model.UserExposedProperty
import repository.UserRepository
import java.util.UUID

class UserService(val repository: UserRepository) {

    fun createUser(
        name: String,
        email: String,
        status: Status,
        interest: String,
        department: String,
        room: String
    ): User {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            status = status,
            interest = interest,
            department = department,
            room = room,
            profilePicture = null,
            friends = emptyList()
        )
        repository.create(user)
        return user
    }

    fun getUser(id: String): User? = repository.getById(id)

    fun deleteUser(id: String) = repository.delete(id)

    fun change(userId: String, property: UserExposedProperty, newValue: Any?) {
        if (newValue == null) {
            require(property == UserExposedProperty.PROFILE_PICTURE) {
                "$property cannot be null"
            }
        } else {
            val expectedType: Class<*> = when (property) {
                UserExposedProperty.NAME,
                UserExposedProperty.EMAIL,
                UserExposedProperty.INTEREST,
                UserExposedProperty.DEPARTMENT,
                UserExposedProperty.ROOM,
                UserExposedProperty.PROFILE_PICTURE -> String::class.java
                UserExposedProperty.STATUS -> Status::class.java
            }
            require(expectedType.isInstance(newValue)) {
                "$property expects ${expectedType.simpleName}, got ${newValue::class.simpleName}"
            }
        }
        repository.updateProperty(userId, property, newValue)
    }

    fun get(userId: String, property: UserExposedProperty): Any? =
        repository.getProperty(userId, property)

    fun sendFriendRequest(fromId: String, toId: String) =
        repository.sendFriendRequest(fromId, toId)

    fun getPendingFriendRequests(userId: String): List<FriendRequest> =
        repository.getPendingFriendRequests(userId)

    fun getFriends(userId: String): List<User> = repository.getFriends(userId)

    fun suggestFriends(userId: String): List<User> = repository.getFriendsOfFriends(userId)

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)
}
```

- [ ] **Step 3: Run unit tests**

Run: `mvn test -pl core`
Expected: `BUILD SUCCESS`, all 19 tests pass

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/service/UserService.kt core/src/test/kotlin/service/UserServiceTest.kt
git commit -m "feat: rewrite UserService with createUser args, change/get, sendFriendRequest"
```

---

### Task 4: TDD Neo4jUserRepository.updateProperty + getProperty

Requires a running Neo4j instance at `bolt://localhost:7687` with credentials `neo4j` / `passwort`.

**Files:**
- Modify: `neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt`
- Modify: `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt`

- [ ] **Step 1: Add failing IT tests for updateProperty and getProperty**

Add the import at the top of `Neo4jUserRepositoryIT.kt`:
```kotlin
import model.UserExposedProperty
```

Add these test methods to the class body:

```kotlin
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
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `mvn test -pl neo4j-impl`
Expected: `BUILD FAILURE` — new tests throw `NotImplementedError` from the stubs

- [ ] **Step 3: Implement updateProperty and getProperty in Neo4jUserRepository.kt**

Replace the two stub overrides (the `TODO("not yet implemented")` ones) with:

```kotlin
override fun updateProperty(id: String, property: UserExposedProperty, value: Any?) {
    val key = property.toNeo4jKey()
    val neo4jValue = if (property == UserExposedProperty.STATUS) (value as Status).name else value
    driver.session().use { session ->
        session.run(
            "MATCH (u:User {id: \$id}) SET u.$key = \$value",
            parameters("id", id, "value", neo4jValue)
        )
    }
}

override fun getProperty(id: String, property: UserExposedProperty): Any? {
    val key = property.toNeo4jKey()
    return driver.session().use { session ->
        val result = session.run(
            "MATCH (u:User {id: \$id}) RETURN u.$key AS value",
            parameters("id", id)
        )
        if (!result.hasNext()) return@use null
        val raw = result.single()["value"]
        if (raw.isNull) return@use null
        if (property == UserExposedProperty.STATUS)
            Status.entries.firstOrNull { it.name == raw.asString() } ?: Status.OFFLINE
        else
            raw.asString()
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl neo4j-impl`
Expected: `BUILD SUCCESS`, all 12 tests pass

- [ ] **Step 5: Commit**

```bash
git add neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt
git commit -m "feat: implement Neo4j updateProperty and getProperty"
```

---

### Task 5: TDD Neo4jUserRepository.sendFriendRequest + getPendingFriendRequests

**Files:**
- Modify: `neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt`
- Modify: `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt`

- [ ] **Step 1: Add failing IT tests**

Add the import at the top of `Neo4jUserRepositoryIT.kt`:
```kotlin
import model.FriendRequest
import org.junit.jupiter.api.Assertions.assertTrue
```

Add these test methods to the class body:

```kotlin
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
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `mvn test -pl neo4j-impl`
Expected: `BUILD FAILURE` — new tests throw `NotImplementedError` from the stubs

- [ ] **Step 3: Implement sendFriendRequest and getPendingFriendRequests in Neo4jUserRepository.kt**

Replace the two stub overrides with:

```kotlin
override fun sendFriendRequest(fromId: String, toId: String) {
    driver.session().use { session ->
        session.run(
            """MATCH (from:User {id: ${'$'}fromId}), (to:User {id: ${'$'}toId})
               OPTIONAL MATCH (to)-[reverse:SENT_REQUEST]->(from)
               WITH from, to, reverse, reverse IS NOT NULL AS isMutual
               CALL {
                 WITH from, to, reverse, isMutual
                 WITH from, to, reverse WHERE isMutual
                 DELETE reverse
                 MERGE (from)-[:FRIENDS_WITH]->(to)
                 MERGE (to)-[:FRIENDS_WITH]->(from)
               }
               CALL {
                 WITH from, to, isMutual
                 WITH from, to WHERE NOT isMutual
                 MERGE (from)-[:SENT_REQUEST]->(to)
               }
               RETURN isMutual""",
            parameters("fromId", fromId, "toId", toId)
        )
    }
}

override fun getPendingFriendRequests(userId: String): List<FriendRequest> {
    return driver.session().use { session ->
        session.run(
            """MATCH (from:User)-[:SENT_REQUEST]->(to:User {id: ${'$'}userId})
               RETURN from.id AS fromId, to.id AS toId""",
            parameters("userId", userId)
        ).list { record ->
            FriendRequest(record["fromId"].asString(), record["toId"].asString())
        }
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all tests in both modules pass

- [ ] **Step 5: Commit**

```bash
git add neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt
git commit -m "feat: implement Neo4j sendFriendRequest (atomic) and getPendingFriendRequests"
```
