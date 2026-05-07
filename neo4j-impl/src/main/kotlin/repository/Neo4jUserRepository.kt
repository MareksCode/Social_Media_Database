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

    //maps enum to corresponding neo4j properties
    private fun UserExposedProperty.toNeo4jPropertyKey(): String = when (this) {
        UserExposedProperty.NAME -> "name"
        UserExposedProperty.EMAIL -> "email"
        UserExposedProperty.STATUS -> "status"
        UserExposedProperty.INTEREST -> "interest"
        UserExposedProperty.DEPARTMENT -> "department"
        UserExposedProperty.ROOM -> "room"
        UserExposedProperty.PROFILE_PICTURE -> "profilePicture"
    }

    //maps node to user model
    private fun Node.toUser(): User = User(
        id = this["id"].asString(),
        name = this["name"].asString(),
        email = this["email"].asString(),
        status = Status.entries.firstOrNull { status -> status.name == this["status"].asString() } ?: Status.OFFLINE,
        interest = this["interest"].asString(),
        department = this["department"].asString(),
        room = this["room"].asString(),
        profilePicture = if (this["profilePicture"].isNull) null else this["profilePicture"].asString()
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

    override fun updateProperty(id: String, property: UserExposedProperty, value: Any?) {
        val neo4jPropertyKey = property.toNeo4jPropertyKey()
        val storedValue = if (property == UserExposedProperty.STATUS) {
            requireNotNull(value) { "STATUS value cannot be null" }
            (value as Status).name
        } else {
            value
        }
        driver.session().use { session ->
            session.run(
                "MATCH (user:User {id: \$userId}) SET user.$neo4jPropertyKey = \$storedValue",
                parameters("userId", id, "storedValue", storedValue)
            )
        }
    }

    override fun getProperty(id: String, property: UserExposedProperty): Any? {
        val neo4jPropertyKey = property.toNeo4jPropertyKey()
        return driver.session().use { session ->
            val queryResult = session.run(
                "MATCH (user:User {id: \$userId}) RETURN user.$neo4jPropertyKey AS propertyValue",
                parameters("userId", id)
            )
            val matchedRecords = queryResult.list()
            if (matchedRecords.isEmpty()) { return@use null }

            val rawPropertyValue = matchedRecords[0]["propertyValue"]
            if (rawPropertyValue.isNull) { return@use null }

            if (property == UserExposedProperty.STATUS) {
                Status.entries.firstOrNull { status -> status.name == rawPropertyValue.asString() } ?: Status.OFFLINE
            } else {
                rawPropertyValue.asObject()
            }
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
