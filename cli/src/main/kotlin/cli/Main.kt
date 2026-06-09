package cli

import model.Status
import model.UserUpdate
import repository.Neo4jUserRepository
import service.UserService

/**
 * Simple interactive terminal app to exercise every UserService feature
 * against a live Neo4j instance.
 *
 * Connection settings (env vars, with defaults):
 *   NEO4J_URI       bolt://localhost:7687
 *   NEO4J_USER      neo4j
 *   NEO4J_PASSWORD  password
 */
fun main() {
    val uri = System.getenv("NEO4J_URI") ?: "bolt://localhost:7687"
    val user = System.getenv("NEO4J_USER") ?: "neo4j"
    val password = System.getenv("NEO4J_PASSWORD") ?: "passwort"

    println("Connecting to $uri as $user ...")
    val repository = try {
        Neo4jUserRepository.connect(uri, user, password)
    } catch (e: Exception) {
        println("Failed to connect: ${e.message}")
        println("Set NEO4J_URI / NEO4J_USER / NEO4J_PASSWORD and ensure Neo4j is running.")
        return
    }
    val service = UserService(repository)
    println("Connected.\n")

    Cli(service).run()
    repository.close()
    println("Bye.")
}

private class Cli(private val service: UserService) {

    fun run() {
        loop@ while (true) {
            printMenu()
            when (prompt("> ").trim()) {
                "1" -> createUser()
                "2" -> getUser()
                "3" -> updateUser()
                "4" -> deleteUser()
                "5" -> sendFriendRequest()
                "6" -> declineFriendRequest()
                "7" -> pendingRequests()
                "8" -> friends()
                "9" -> recommendations()
                "10" -> removeFriend()
                "11" -> seedTestUsers()
                "0", "q", "quit", "exit" -> break@loop
                "" -> {}
                else -> println("Unknown option.")
            }
            println()
        }
    }

    private fun printMenu() {
        println(
            """
            ======== UserService test menu ========
             1) Create user
             2) Get user by id
             3) Update user
             4) Delete user
             5) Send friend request
             6) Decline friend request
             7) List pending (incoming) requests
             8) List friends
             9) Friend recommendations
            10) Remove friend
            11) Seed 10 test users
             0) Quit
            =======================================
            """.trimIndent()
        )
    }

    private fun createUser() {
        val name = prompt("Name: ")
        val email = prompt("Email: ")
        val status = promptStatus()
        val interest = prompt("Interest: ")
        val department = prompt("Department: ")
        val room = prompt("Room: ")
        val created = service.createUser(name, email, status, interest, department, room)
        println("Created user:")
        printUser(created)
        println("id = ${created.id}  (copy this for other actions)")
    }

    private fun getUser() {
        val id = prompt("User id: ")
        val u = service.getUser(id)
        if (u == null) println("No user with id $id") else printUser(u)
    }

    private fun updateUser() {
        val id = prompt("User id to update: ")
        println("Leave a field blank to keep current value.")
        val name = promptOrNull("Name: ")
        val email = promptOrNull("Email: ")
        val statusRaw = promptOrNull("Status (ONLINE/OFFLINE/BUSY): ")
        val interest = promptOrNull("Interest: ")
        val department = promptOrNull("Department: ")
        val room = promptOrNull("Room: ")
        val profilePicture = promptOrNull("Profile picture: ")
        val status = statusRaw?.let { parseStatus(it) }
        service.updateUser(
            id,
            UserUpdate(
                name = name,
                email = email,
                status = status,
                interest = interest,
                department = department,
                room = room,
                profilePicture = profilePicture
            )
        )
        println("Updated. Current state:")
        service.getUser(id)?.let { printUser(it) } ?: println("(user not found)")
    }

    private fun deleteUser() {
        val id = prompt("User id to delete: ")
        service.deleteUser(id)
        println("Deleted (if existed).")
    }

    private fun sendFriendRequest() {
        val from = prompt("From id: ")
        val to = prompt("To id: ")
        if (service.getUser(from) == null) { println("Error: no user with id $from"); return }
        if (service.getUser(to) == null) { println("Error: no user with id $to"); return }
        try {
            service.sendFriendRequest(from, to)
            println("Request sent. If a reverse request existed, you are now friends.")
        } catch (e: IllegalArgumentException) {
            println("Error: ${e.message}")
        }
    }

    private fun declineFriendRequest() {
        val userId = prompt("Your id (recipient): ")
        val from = prompt("Requester id to decline: ")
        service.declineFriendRequest(userId, from)
        println("Declined (if existed).")
    }

    private fun pendingRequests() {
        val id = prompt("User id: ")
        val reqs = service.getPendingFriendRequests(id)
        if (reqs.isEmpty()) {
            println("No pending requests.")
        } else {
            reqs.forEach { println("  from=${it.fromId}  at ${it.sendTime}") }
        }
    }

    private fun friends() {
        val id = prompt("User id: ")
        val list = service.getFriends(id)
        if (list.isEmpty()) {
            println("No friends.")
        } else {
            list.forEach { println("  ${it.friend.name} (${it.friend.id})  since ${it.createTime}") }
        }
    }

    private fun recommendations() {
        val id = prompt("User id: ")
        val recs = service.getFriendRecommendations(id)
        if (recs.isEmpty()) {
            println("No recommendations.")
        } else {
            recs.forEach { println("  ${it.name} (${it.id})") }
        }
    }

    private fun removeFriend() {
        val id = prompt("Your id: ")
        val friend = prompt("Friend id to remove: ")
        service.removeFriend(id, friend)
        println("Removed (if existed).")
    }

    private fun seedTestUsers() {
        val seeds = listOf(
            SeedData("Alice", "alice@example.com", Status.ONLINE, "clash royale", "IT", "007"),
            SeedData("Bob", "bob@example.com", Status.OFFLINE, "chess", "HR", "101"),
            SeedData("Carol", "carol@example.com", Status.BUSY, "hiking", "Sales", "202"),
            SeedData("Dave", "dave@example.com", Status.ONLINE, "guitar", "IT", "008"),
            SeedData("Eve", "eve@example.com", Status.OFFLINE, "painting", "Design", "303"),
            SeedData("Frank", "frank@example.com", Status.ONLINE, "cycling", "IT", "009"),
            SeedData("Grace", "grace@example.com", Status.BUSY, "cooking", "HR", "102"),
            SeedData("Heidi", "heidi@example.com", Status.ONLINE, "running", "Sales", "203"),
            SeedData("Ivan", "ivan@example.com", Status.OFFLINE, "gaming", "Design", "304"),
            SeedData("Judy", "judy@example.com", Status.ONLINE, "reading", "IT", "010")
        )
        // create all
        val id = mutableMapOf<String, String>()
        println("Created ${seeds.size} test users:")
        seeds.forEach { s ->
            val u = service.createUser(s.name, s.email, s.status, s.interest, s.department, s.room)
            id[s.name] = u.id
            println("  ${u.name.padEnd(6)} id=${u.id}")
        }

        // friendship line of 6 + one small circle (triangle Alice-Bob-Carol)
        val friendships = listOf(
            "Alice" to "Bob",
            "Bob" to "Carol",
            "Carol" to "Dave",
            "Dave" to "Eve",
            "Eve" to "Frank",
            "Alice" to "Carol" // closes triangle -> small circle
        )
        friendships.forEach { (a, b) ->
            service.sendFriendRequest(id.getValue(a), id.getValue(b))
            service.sendFriendRequest(id.getValue(b), id.getValue(a)) // reverse auto-accepts
        }

        // 3 pending requests to Dave (a user within the friend line)
        listOf("Grace", "Heidi", "Ivan").forEach { sender ->
            service.sendFriendRequest(id.getValue(sender), id.getValue("Dave"))
        }

        println()
        println("Friend line:  Alice - Bob - Carol - Dave - Eve - Frank")
        println("Small circle: Alice - Bob - Carol - Alice (triangle)")
        println("Pending -> Dave from: Grace, Heidi, Ivan")
        println("Unconnected: Judy")
    }

    private data class SeedData(
        val name: String, val email: String, val status: Status,
        val interest: String, val department: String, val room: String
    )

    // ---- helpers ----

    private fun printUser(u: model.User) {
        println("  id:         ${u.id}")
        println("  name:       ${u.name}")
        println("  email:      ${u.email}")
        println("  status:     ${u.status}")
        println("  interest:   ${u.interest}")
        println("  department: ${u.department}")
        println("  room:       ${u.room}")
        println("  picture:    ${u.profilePicture}")
    }

    private fun prompt(label: String): String {
        print(label)
        return (readlnOrNull() ?: "").trim()
    }

    private fun promptOrNull(label: String): String? =
        prompt(label).ifBlank { null }

    private fun promptStatus(): Status {
        while (true) {
            val raw = prompt("Status (ONLINE/OFFLINE/BUSY) [ONLINE]: ").trim()
            if (raw.isBlank()) return Status.ONLINE
            parseStatus(raw)?.let { return it }
            println("Invalid status.")
        }
    }

    private fun parseStatus(raw: String): Status? =
        runCatching { Status.valueOf(raw.trim().uppercase()) }.getOrNull()
}
