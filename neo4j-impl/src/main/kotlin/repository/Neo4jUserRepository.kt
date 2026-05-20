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
        fun connect(uri: String, username: String, password: String): Neo4jUserRepository =
            Neo4jUserRepository(GraphDatabase.driver(uri, AuthTokens.basic(username, password)))
    }

    private fun Node.toUser(): User = User(
        id = this["id"].asString(),
        name = this["name"].asString(),
        email = this["email"].asString(),
        status = Status.entries.firstOrNull { status -> status.name == this["status"].asString() } ?: Status.OFFLINE,
        interest = this["interest"].asString(),
        department = this["department"].asString(),
        room = this["room"].asString(),
        profilePicture = this["profilePicture"].asString("")
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
            val queryResult = session.run(
                "MATCH (user:User {id: \$userId}) RETURN user",
                parameters("userId", id)
            )
            val matchedRecords = queryResult.list()
            if (matchedRecords.isEmpty()) return null
            return matchedRecords[0]["user"].asNode().toUser()
        }
    }

    override fun delete(id: String) {
        driver.session().use { session ->
            session.run(
                "MATCH (user:User {id: \$userId}) DETACH DELETE user",
                parameters("userId", id)
            )
        }
    }

    override fun updateUser(id: String, update: UserExposedProperty) {
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
            session.run(
                "MATCH (user:User {id: \$userId}) SET user += \$props",
                parameters("userId", id, "props", props)
            )
        }
    }

    override fun sendFriendRequest(fromId: String, toId: String) {
        driver.session().use { session ->
            session.run(
                """MATCH (sender:User {id: ${'$'}senderId}), (receiver:User {id: ${'$'}receiverId})
                   OPTIONAL MATCH (receiver)-[existingRequest:SENT_REQUEST]->(sender)
                   WITH sender, receiver, existingRequest, existingRequest IS NOT NULL AS isMutualRequest
                   CALL (sender, receiver, existingRequest, isMutualRequest) {
                     WITH sender, receiver, existingRequest WHERE isMutualRequest
                     DELETE existingRequest
                     MERGE (sender)-[:FRIENDS_WITH]->(receiver)
                     MERGE (receiver)-[:FRIENDS_WITH]->(sender)
                   }
                   CALL (sender, receiver, isMutualRequest) {
                     WITH sender, receiver WHERE NOT isMutualRequest
                     MERGE (sender)-[:SENT_REQUEST]->(receiver)
                   }""",
                parameters("senderId", fromId, "receiverId", toId)
            )
        }
    }

    override fun getPendingFriendRequests(userId: String): List<FriendRequest> {
        return driver.session().use { session ->
            session.run(
                """MATCH (requester:User)-[:SENT_REQUEST]->(recipient:User {id: ${'$'}userId})
                   RETURN requester.id AS requesterId, recipient.id AS recipientId""",
                parameters("userId", userId)
            ).list { record ->
                FriendRequest(record["requesterId"].asString(), record["recipientId"].asString())
            }
        }
    }

    override fun getFriends(userId: String): List<User> {
        driver.session().use { session ->
            val queryResult = session.run(
                "MATCH (user:User {id: \$userId})-[:FRIENDS_WITH]->(friend:User) RETURN friend",
                parameters("userId", userId)
            )
            return queryResult.list { record -> record["friend"].asNode().toUser() }
        }
    }

    override fun removeFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            session.run(
                """MATCH (user:User {id: ${'$'}userId})-[userToFriend:FRIENDS_WITH]->(friend:User {id: ${'$'}friendId})
                   OPTIONAL MATCH (friend)-[friendToUser:FRIENDS_WITH]->(user)
                   DELETE userToFriend, friendToUser""",
                parameters("userId", userId, "friendId", friendId)
            )
        }
    }

    override fun getFriendsOfFriends(userId: String): List<User> {
        driver.session().use { session ->
            val queryResult = session.run(
                """MATCH (targetUser:User {id: ${'$'}userId})-[:FRIENDS_WITH]->(directFriend)-[:FRIENDS_WITH]->(friendOfFriend)
                   WHERE NOT (targetUser)-[:FRIENDS_WITH]->(friendOfFriend) AND friendOfFriend <> targetUser
                   WITH friendOfFriend, count(directFriend) AS sharedFriendCount
                   ORDER BY sharedFriendCount DESC
                   RETURN friendOfFriend""",
                parameters("userId", userId)
            )
            return queryResult.list { record -> record["friendOfFriend"].asNode().toUser() }
        }
    }

    fun close() = driver.close()
}
