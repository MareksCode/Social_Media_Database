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
