package repository

import model.Status
import model.User
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Values.parameters
import org.neo4j.driver.types.Node

class Neo4jUserRepository(private val driver: Driver) : UserRepository {
    companion object { //static 
        fun connect(uri: String, user: String, password: String): Neo4jUserRepository = Neo4jUserRepository(GraphDatabase.driver(uri, AuthTokens.basic(user, password)))
    }

    private fun nodeToUser(node: Node, friendIds: List<String> = emptyList()): User = User(
        id = node["id"].asString(),
        name = node["name"].asString(),
        email = node["email"].asString(),
        status = Status.entries.firstOrNull { //returns first matching entry or null
            it.name == node["status"].asString() 
        } ?: Status.OFFLINE, //if null
        interest = node["interest"].asString(),
        department = node["department"].asString(),
        room = node["room"].asString(),
        profilePicture = if (node["profilePicture"].isNull) null 
                        else node["profilePicture"].asString(),
        friends = friendIds
    )

    override fun create(user: User) {
        driver.session().use { session ->
            session.run(
                """CREATE (u:User {
                    id: ${'$'}id, name: ${'$'}name, email: ${'$'}email, status: ${'$'}status,
                    interest: ${'$'}interest, department: ${'$'}department, room: ${'$'}room,
                    profilePicture: ${'$'}profilePicture
                })""",
                parameters(
                    "id", user.id, "name", user.name, "email", user.email,
                    "status", user.status.name, "interest", user.interest,
                    "department", user.department, "room", user.room,
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

    override fun update(user: User) {
        driver.session().use { session ->
            session.run(
                """MATCH (u:User {id: ${'$'}id}) SET u += {
                    name: ${'$'}name, email: ${'$'}email, status: ${'$'}status,
                    interest: ${'$'}interest, department: ${'$'}department, room: ${'$'}room,
                    profilePicture: ${'$'}profilePicture
                }""",
                parameters(
                    "id", user.id, "name", user.name, "email", user.email,
                    "status", user.status.name, "interest", user.interest,
                    "department", user.department, "room", user.room,
                    "profilePicture", user.profilePicture
                )
            )
        }
    }

    override fun delete(id: String) {
        driver.session().use { session ->
            session.run("MATCH (u:User {id: \$id}) DETACH DELETE u", parameters("id", id))
        }
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

    override fun addFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            session.run(
                """MATCH (a:User {id: ${'$'}userId}), (b:User {id: ${'$'}friendId})
               MERGE (a)-[:FRIENDS_WITH]->(b)
               MERGE (b)-[:FRIENDS_WITH]->(a)""",
                parameters("userId", userId, "friendId", friendId)
            )
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
            val result = session.run(
                """MATCH (u:User {id: ${'$'}id})-[:FRIENDS_WITH]->(f)-[:FRIENDS_WITH]->(fof)
                   WHERE NOT (u)-[:FRIENDS_WITH]->(fof) AND fof <> u
                   WITH fof, count(f) AS commonCount
                   OPTIONAL MATCH (fof)-[:FRIENDS_WITH]->(fofFriend:User)
                   WITH fof, commonCount, collect(fofFriend.id) AS friendIds
                   ORDER BY commonCount DESC
                   RETURN fof, friendIds""",
                parameters("id", userId)
            )
            return result.list { record ->
                nodeToUser(record["fof"].asNode(), record["friendIds"].asList { it.asString() })
            }
        }
    }

    fun close() = driver.close()
}
