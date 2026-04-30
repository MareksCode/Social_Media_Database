package repository

import model.Status
import model.User
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

    private fun nodeToUser(node: Node): User = User(
        id = node["id"].asString(),
        name = node["name"].asString(),
        email = node["email"].asString(),
        status = Status.valueOf(node["status"].asString()),
        interest = node["interest"].asString(),
        abteilung = node["abteilung"].asString(),
        raum = node["raum"].asString(),
        profilbild = if (node["profilbild"].isNull) null else node["profilbild"].asString(),
        friends = node["friends"].asList { it.asString() }
    )

    override fun create(user: User) {
        driver.session().use { session ->
            session.run(
                """CREATE (u:User {
                    id: ${'$'}id, name: ${'$'}name, email: ${'$'}email, status: ${'$'}status,
                    interest: ${'$'}interest, abteilung: ${'$'}abteilung, raum: ${'$'}raum,
                    profilbild: ${'$'}profilbild, friends: ${'$'}friends
                })""",
                parameters(
                    "id", user.id, "name", user.name, "email", user.email,
                    "status", user.status.name, "interest", user.interest,
                    "abteilung", user.abteilung, "raum", user.raum,
                    "profilbild", user.profilbild, "friends", user.friends
                )
            )
        }
    }

    override fun getById(id: String): User? {
        driver.session().use { session ->
            val result = session.run("MATCH (u:User {id: \$id}) RETURN u", parameters("id", id))
            return if (result.hasNext()) nodeToUser(result.single()["u"].asNode()) else null
        }
    }

    override fun update(user: User) {
        driver.session().use { session ->
            session.run(
                """MATCH (u:User {id: ${'$'}id}) SET u += {
                    name: ${'$'}name, email: ${'$'}email, status: ${'$'}status,
                    interest: ${'$'}interest, abteilung: ${'$'}abteilung, raum: ${'$'}raum,
                    profilbild: ${'$'}profilbild, friends: ${'$'}friends
                }""",
                parameters(
                    "id", user.id, "name", user.name, "email", user.email,
                    "status", user.status.name, "interest", user.interest,
                    "abteilung", user.abteilung, "raum", user.raum,
                    "profilbild", user.profilbild, "friends", user.friends
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
                "MATCH (u:User {id: \$id})-[:FRIENDS_WITH]->(f:User) RETURN f",
                parameters("id", userId)
            )
            return result.list { nodeToUser(it["f"].asNode()) }
        }
    }

    override fun addFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            session.run(
                "MATCH (a:User {id: \$userId}), (b:User {id: \$friendId}) MERGE (a)-[:FRIENDS_WITH]->(b)",
                parameters("userId", userId, "friendId", friendId)
            )
        }
    }

    override fun removeFriend(userId: String, friendId: String) {
        driver.session().use { session ->
            session.run(
                "MATCH (a:User {id: \$userId})-[r:FRIENDS_WITH]->(b:User {id: \$friendId}) DELETE r",
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
                   ORDER BY commonCount DESC
                   RETURN fof""",
                parameters("id", userId)
            )
            return result.list { nodeToUser(it["fof"].asNode()) }
        }
    }

    fun close() = driver.close()
}
