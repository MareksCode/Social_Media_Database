package repository

import model.FriendRequest
import model.Friendship
import model.Status
import model.User
import model.UserUpdate
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Values.parameters
import org.neo4j.driver.types.Node
import java.time.Instant

class Neo4jUserRepository(private val driver: Driver) : UserRepository {
    companion object {
        // companion object = static-like factory method
        fun connect(uri: String, username: String, password: String): Neo4jUserRepository =
            Neo4jUserRepository(GraphDatabase.driver(uri, AuthTokens.basic(username, password)))
                .also { it.ensureSchema() }
    }

    // ensures unique user id constraint & c reates lookup table -> Better performance
    fun ensureSchema() {
        driver.session().use { session ->
            session.run("CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE")
        }
    }

    // extension function: adds toUser() method to Neo4j's Node type
    private fun Node.toUser(): User = User(
        id = this["id"].asString(),
        name = this["name"].asString(),
        email = this["email"].asString(),
        status = runCatching { Status.valueOf(this["status"].asString()) }.getOrDefault(Status.OFFLINE),
        interest = this["interest"].asString(),
        department = this["department"].asString(),
        room = this["room"].asString(),
        profilePicture = this["profilePicture"].asString("")
    )

    override fun create(user: User) {
        // .use{} closes the session automatically after the block
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

    override fun getById(id: String): User? = driver.session().use { session ->
        session.run(
            "MATCH (user:User {id: \$userId}) RETURN user",
            parameters("userId", id)
        ).list().firstOrNull()?.get("user")?.asNode()?.toUser()
    }

    override fun delete(id: String) {
        driver.session().use { session ->
            // DETACH DELETE removes the node and all its relationships
            session.run(
                "MATCH (user:User {id: \$userId}) DETACH DELETE user",
                parameters("userId", id)
            )
        }
    }

    override fun update(id: String, update: UserUpdate) {
        val props = buildMap<String, Any?> {
            update.name?.let { put("name", it) }
            update.email?.let { put("email", it) }
            update.status?.let { put("status", it.name) }
            update.interest?.let { put("interest", it) }
            update.department?.let { put("department", it) }
            update.room?.let { put("room", it) }
            update.profilePicture?.let { put("profilePicture", it) }
        }
        if (props.isEmpty()) return
        driver.session().use { session ->
            // SET user += $props merges only provided fields, leaving others unchanged
            session.run(
                "MATCH (user:User {id: \$userId}) SET user += \$props",
                parameters("userId", id, "props", props)
            )
        }
    }

    override fun addFriendRequest(fromId: String, toId: String, sendTime: Instant) {
        driver.session().use { session ->
            // MERGE = create relationship if it doesn't exist
            // ${'$'} -> escape $
            session.run(
                """MATCH (sender:User {id: ${'$'}senderId}), (receiver:User {id: ${'$'}receiverId})
                   MERGE (sender)-[r:SENT_REQUEST]->(receiver)
                   ON CREATE SET r.sentTimeEpochMs = ${'$'}sentTimeEpochMs""",
                parameters("senderId", fromId, "receiverId", toId, "sentTimeEpochMs", sendTime.toEpochMilli())
            )
        }
    }

    override fun removeFriendRequest(fromId: String, toId: String) {
        driver.session().use { session ->
            session.run(
                "MATCH (sender:User {id: \$senderId})-[r:SENT_REQUEST]->(receiver:User {id: \$receiverId}) DELETE r",
                parameters("senderId", fromId, "receiverId", toId)
            )
        }
    }

    override fun friendRequestExists(fromId: String, toId: String): Boolean {
        return driver.session().use { session ->
            session.run(
                """MATCH (sender:User {id: ${'$'}senderId})-[r:SENT_REQUEST]->(receiver:User {id: ${'$'}receiverId})
                   RETURN count(r) > 0 AS exists""",
                parameters("senderId", fromId, "receiverId", toId)
            ).single()["exists"].asBoolean()
        }
    }

    override fun getIncomingFriendRequests(userId: String): List<FriendRequest> {
        return driver.session().use { session ->
            session.run(
                """MATCH (requester:User)-[r:SENT_REQUEST]->(recipient:User {id: ${'$'}userId})
                   RETURN requester.id AS requesterId, recipient.id AS recipientId, r.sentTimeEpochMs AS sentTimeEpochMs""",
                parameters("userId", userId)
            ).list { record ->
                FriendRequest(
                    record["requesterId"].asString(),
                    record["recipientId"].asString(),
                    Instant.ofEpochMilli(record["sentTimeEpochMs"].asLong())
                )
            }
        }
    }

    override fun acceptFriendRequest(fromId: String, toId: String, createTime: Instant) {
        driver.session().use { session ->
            session.run(
                """MATCH (sender:User {id: ${'$'}senderId}), (receiver:User {id: ${'$'}receiverId})
                   OPTIONAL MATCH (receiver)-[rev:SENT_REQUEST]->(sender)
                   DELETE rev
                   MERGE (sender)-[r:FRIENDS_WITH]-(receiver)
                   ON CREATE SET r.createdAtEpochMs = ${'$'}createdAtEpochMs""",
                parameters(
                    "senderId", fromId,
                    "receiverId", toId,
                    "createdAtEpochMs", createTime.toEpochMilli()
                )
            )
        }
    }

    // test helper: create friendship directly
    fun addFriend(userId: String, friendId: String, createTime: Instant) {
        driver.session().use { session ->
            session.run(
                """MATCH (user:User {id: ${'$'}userId}), (friend:User {id: ${'$'}friendId})
                   MERGE (user)-[r:FRIENDS_WITH]-(friend)
                   ON CREATE SET r.createdAtEpochMs = ${'$'}createdAtEpochMs""",
                parameters("userId", userId, "friendId", friendId, "createdAtEpochMs", createTime.toEpochMilli())
            )
        }
    }

    override fun removeFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            // undirected match, deletes both directions
            session.run(
                """MATCH (user:User {id: ${'$'}userId})-[r:FRIENDS_WITH]-(friend:User {id: ${'$'}friendId})
                   DELETE r""",
                parameters("userId", userId, "friendId", friendId)
            )
        }
    }

    override fun areFriends(userId: String, friendId: String): Boolean {
        return driver.session().use { session ->
            session.run(
                """MATCH (user:User {id: ${'$'}userId})-[r:FRIENDS_WITH]-(friend:User {id: ${'$'}friendId})
                   RETURN count(r) > 0 AS areFriends""",
                parameters("userId", userId, "friendId", friendId)
            ).single()["areFriends"].asBoolean()
        }
    }

    override fun getFriends(userId: String): List<Friendship> = driver.session().use { session ->
        session.run(
            """MATCH (user:User {id: ${'$'}userId})-[r:FRIENDS_WITH]-(friend:User)
               RETURN user.id AS userId, friend.id AS friendId, r.createdAtEpochMs AS createdAtEpochMs""",
            parameters("userId", userId)
        ).list { record ->
            Friendship(
                record["userId"].asString(),
                record["friendId"].asString(),
                Instant.ofEpochMilli(record["createdAtEpochMs"].asLong())
            )
        }
    }

    override fun getFriendsOf(userIds: Collection<String>): Map<String, List<Friendship>> {
        if (userIds.isEmpty()) return emptyMap()
        return driver.session().use { session ->
            session.run(
                """MATCH (user:User)-[r:FRIENDS_WITH]-(friend:User)
                   WHERE user.id IN ${'$'}userIds
                   RETURN user.id AS userId, friend.id AS friendId, r.createdAtEpochMs AS createdAtEpochMs""",
                parameters("userIds", userIds.toList())
            ).list { record ->
                record["userId"].asString() to Friendship(
                    record["userId"].asString(),
                    record["friendId"].asString(),
                    Instant.ofEpochMilli(record["createdAtEpochMs"].asLong())
                )
            }.groupBy({ it.first }, { it.second }) // group pairs into Map<userId, List<Friendship>>
        }
    }

    fun close() = driver.close()
}
