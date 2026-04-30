# Neo4j Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure project into a multi-module Maven build with a domain service layer and a Neo4j repository implementation.

**Architecture:** Parent POM manages versions; `core` module holds domain model, repository interface, and UserService; `neo4j-impl` module is the external module that implements UserRepository using the official Neo4j Java driver with manual Cypher.

**Tech Stack:** Kotlin 2.1.0, Maven, neo4j-java-driver 5.26.0, Neo4j 5.x

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `pom.xml` | Parent POM — Kotlin plugin, dep versions, module list |
| Create | `core/pom.xml` | Core module POM — kotlin-stdlib only |
| Move | `core/src/main/kotlin/model/Status.kt` | Status enum (from `src/model/Status.kt`) |
| Move | `core/src/main/kotlin/model/User.kt` | User data class (from `src/model/User.kt`) |
| Move | `core/src/main/kotlin/repository/UserRepository.kt` | Repository interface (from `src/repository/UserRepository.kt`) |
| Create | `core/src/main/kotlin/service/UserService.kt` | Application service — delegates to UserRepository |
| Create | `neo4j-impl/pom.xml` | Neo4j module POM — depends on core + neo4j-java-driver |
| Create | `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt` | Neo4j implementation of UserRepository |
| Create | `docs/neo4j-justification.md` | Written justification for Neo4j choice |

---

## Task 1: Create parent pom.xml

**Files:**
- Create: `pom.xml`

- [ ] **Step 1: Create parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>db-parent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>core</module>
        <module>neo4j-impl</module>
    </modules>

    <properties>
        <kotlin.version>2.1.0</kotlin.version>
        <neo4j.driver.version>5.26.0</neo4j.driver.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-stdlib</artifactId>
                <version>${kotlin.version}</version>
            </dependency>
            <dependency>
                <groupId>org.neo4j.driver</groupId>
                <artifactId>neo4j-java-driver</artifactId>
                <version>${neo4j.driver.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>core</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <goals><goal>compile</goal></goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
                            </sourceDirs>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Commit**

```bash
git add pom.xml
git commit -m "build: add parent Maven POM"
```

---

## Task 2: Create core module POM and directory structure

**Files:**
- Create: `core/pom.xml`
- Create dirs: `core/src/main/kotlin/model/`, `core/src/main/kotlin/repository/`, `core/src/main/kotlin/service/`

- [ ] **Step 1: Create core/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>db-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>core</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p core/src/main/kotlin/model
mkdir -p core/src/main/kotlin/repository
mkdir -p core/src/main/kotlin/service
```

- [ ] **Step 3: Commit**

```bash
git add core/pom.xml
git commit -m "build: add core module POM"
```

---

## Task 3: Move existing Kotlin files into core module

**Files:**
- Move: `src/model/Status.kt` → `core/src/main/kotlin/model/Status.kt`
- Move: `src/model/User.kt` → `core/src/main/kotlin/model/User.kt`
- Move: `src/repository/UserRepository.kt` → `core/src/main/kotlin/repository/UserRepository.kt`

- [ ] **Step 1: Move files with git mv**

```bash
git mv src/model/Status.kt core/src/main/kotlin/model/Status.kt
git mv src/model/User.kt core/src/main/kotlin/model/User.kt
git mv src/repository/UserRepository.kt core/src/main/kotlin/repository/UserRepository.kt
```

- [ ] **Step 2: Verify file contents are unchanged**

`core/src/main/kotlin/model/Status.kt` must contain:
```kotlin
package model

enum class Status {
    ONLINE, OFFLINE, BUSY
}
```

`core/src/main/kotlin/model/User.kt` must contain:
```kotlin
package model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val status: Status,
    val interest: String,
    val abteilung: String,
    val raum: String,
    val profilbild: String?,
    val friends: List<String>
)
```

`core/src/main/kotlin/repository/UserRepository.kt` must contain:
```kotlin
package repository

import model.User

interface UserRepository {
    fun create(user: User)
    fun getById(id: String): User?
    fun update(user: User)
    fun delete(id: String)
    fun getFriends(userId: String): List<User>
    fun addFriend(userId: String, friendId: String)
    fun removeFriend(userId: String, friendId: String)
    fun getFriendsOfFriends(userId: String): List<User>
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: move domain model and repository interface into core module"
```

---

## Task 4: Create UserService in core module

**Files:**
- Create: `core/src/main/kotlin/service/UserService.kt`

- [ ] **Step 1: Create UserService.kt**

```kotlin
package service

import model.User
import repository.UserRepository

class UserService(private val repository: UserRepository) {

    fun createUser(user: User) = repository.create(user)

    fun getUser(id: String): User? = repository.getById(id)

    fun updateUser(user: User) = repository.update(user)

    fun deleteUser(id: String) = repository.delete(id)

    fun addFriend(userId: String, friendId: String) = repository.addFriend(userId, friendId)

    fun removeFriend(userId: String, friendId: String) = repository.removeFriend(userId, friendId)

    fun getFriends(userId: String): List<User> = repository.getFriends(userId)

    fun suggestFriends(userId: String): List<User> = repository.getFriendsOfFriends(userId)
}
```

- [ ] **Step 2: Compile core module to verify**

```bash
mvn compile -pl core
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/service/UserService.kt
git commit -m "feat: add UserService application layer"
```

---

## Task 5: Create neo4j-impl module POM and directory structure

**Files:**
- Create: `neo4j-impl/pom.xml`
- Create dirs: `neo4j-impl/src/main/kotlin/repository/`

- [ ] **Step 1: Create neo4j-impl/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>db-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>neo4j-impl</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.neo4j.driver</groupId>
            <artifactId>neo4j-java-driver</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create directory structure**

```bash
mkdir -p neo4j-impl/src/main/kotlin/repository
```

- [ ] **Step 3: Commit**

```bash
git add neo4j-impl/pom.xml
git commit -m "build: add neo4j-impl module POM"
```

---

## Task 6: Implement Neo4jUserRepository

**Files:**
- Create: `neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt`

- [ ] **Step 1: Create Neo4jUserRepository.kt**

```kotlin
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
```

- [ ] **Step 2: Compile full project**

```bash
mvn compile
```

Expected: `BUILD SUCCESS` for both `core` and `neo4j-impl`

- [ ] **Step 3: Commit**

```bash
git add neo4j-impl/src/main/kotlin/repository/Neo4jUserRepository.kt
git commit -m "feat: implement UserRepository for Neo4j using official Java driver"
```

---

## Task 7: Write Neo4j justification document

**Files:**
- Create: `docs/neo4j-justification.md`

- [ ] **Step 1: Create justification document**

```markdown
# Begründung: Neo4j als Datenbanktechnologie

## Domäne

Das Domänenmodell beschreibt Nutzer in einem sozialen Netzwerk. Kernoperationen:
- Freundschaften verwalten (`addFriend`, `removeFriend`, `getFriends`)
- Freunde zweiter Ordnung ermitteln (`getFriendsOfFriends`), sortiert nach gemeinsamen Freunden

Diese Operationen sind **Graph-Traversals** — sie folgen Kanten (Beziehungen) zwischen Knoten (Nutzern).

## Warum Neo4j?

### 1. Natürliche Modellierung

Freundschaften sind Kanten im Graphen:
```
(alice:User)-[:FRIENDS_WITH]->(bob:User)
```
Das Modell entspricht direkt der Domäne — keine JOIN-Tabellen, keine Fremdschlüssel-Arrays.

### 2. Effiziente Graphabfragen

`getFriendsOfFriends` in Cypher:
```cypher
MATCH (u:User {id: $id})-[:FRIENDS_WITH]->(f)-[:FRIENDS_WITH]->(fof)
WHERE NOT (u)-[:FRIENDS_WITH]->(fof) AND fof <> u
WITH fof, count(f) AS commonCount
ORDER BY commonCount DESC
RETURN fof
```

Äquivalent in SQL würde drei Self-Joins auf einer `friendships`-Tabelle benötigen und skaliert quadratisch mit der Datenmenge. Neo4j traversiert Kanten in O(1) pro Hop — unabhängig von der Gesamtgröße des Graphen (Index-Free Adjacency).

### 3. Skalierbarkeit für soziale Graphen

Neo4j ist auf tiefe Traversals optimiert. Bei 1 Million Nutzern bleibt eine 2-Hop-Abfrage schnell, weil die Engine nur die relevanten Kanten liest — nicht die gesamte Tabelle.

### 4. Schemaflexibilität

Nutzerprofile können erweitert werden (neue Felder wie `profilbild`) ohne Migrationsskripte. Neo4j-Knoten sind schema-optional.

## Alternativen und Abwägung

| Technologie | Stärke | Schwäche für diese Domäne |
|-------------|--------|--------------------------|
| PostgreSQL | ACID, reif, weit verbreitet | Self-Joins für Graphabfragen teuer |
| MongoDB | Flexibles Schema | Keine nativen Graphabfragen |
| Redis | Sehr schnell für einfache Strukturen | Kein Graphmodell |
| **Neo4j** | **Native Graphabfragen, Cypher, Index-Free Adjacency** | **Weniger verbreitet als relationale DBs** |

## Fazit

Neo4j ist die natürlichste Wahl für ein soziales Netzwerk mit Freundschaftsbeziehungen und Empfehlungsabfragen (Freunde zweiter Ordnung). Die Abfragesprache Cypher bildet die Domäne direkt ab und ist effizienter als SQL-Äquivalente für Graph-Traversals.
```

- [ ] **Step 2: Commit**

```bash
git add docs/neo4j-justification.md
git commit -m "docs: add Neo4j technology justification"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Multi-module Maven structure (Tasks 1–2, 5)
- ✅ Existing files moved to core module (Task 3)
- ✅ UserService domain logic sketch (Task 4)
- ✅ Neo4jUserRepository external module (Tasks 5–6)
- ✅ DB justification document (Task 7)
- ✅ No UML (per project decision)

**Placeholder scan:** No TBD/TODO in any code blocks.

**Type consistency:**
- `nodeToUser(node: Node)` defined in Task 6 — used only within Task 6 ✅
- `UserRepository` interface methods match `Neo4jUserRepository` overrides exactly ✅
- `UserService` method signatures delegate to exact `UserRepository` method names ✅
