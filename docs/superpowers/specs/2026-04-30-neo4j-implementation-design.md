# Neo4j Implementation Design

**Date:** 2026-04-30  
**Scope:** Multi-module Maven project with domain logic sketch and Neo4j repository implementation

---

## Overview

Extend existing Kotlin project into a multi-module Maven build. Core module holds domain model and application service; `neo4j-impl` module is the external repository implementation using the official Neo4j Java driver with manual Cypher queries.

No UML diagram required per project decision.

---

## Module Structure

```
DB/
├── pom.xml                   parent POM (packaging=pom, manages deps)
├── core/
│   ├── pom.xml
│   └── src/main/kotlin/
│       ├── model/
│       │   ├── Status.kt     (moved from src/)
│       │   └── User.kt       (moved from src/)
│       ├── repository/
│       │   └── UserRepository.kt  (moved from src/)
│       └── service/
│           └── UserService.kt     (NEW)
└── neo4j-impl/
    ├── pom.xml               depends on core
    └── src/main/kotlin/
        └── repository/
            └── Neo4jUserRepository.kt  (NEW)
```

---

## Domain Logic Sketch — UserService

`UserService` receives a `UserRepository` via constructor (dependency injection by hand). It exposes application-level operations that coordinate repository calls. No persistence logic lives here.

Methods:
- `createUser(user: User)` — delegates to `repository.create`
- `getUser(id: String): User?` — delegates to `repository.getById`
- `updateUser(user: User)` — delegates to `repository.update`
- `deleteUser(id: String)` — delegates to `repository.delete`
- `addFriend(userId: String, friendId: String)` — delegates to `repository.addFriend`
- `removeFriend(userId: String, friendId: String)` — delegates to `repository.removeFriend`
- `getFriends(userId: String): List<User>` — delegates to `repository.getFriends`
- `suggestFriends(userId: String): List<User>` — delegates to `repository.getFriendsOfFriends`

This is intentionally sketched (thin service). Real domain logic would live here if business rules existed beyond CRUD.

---

## Neo4j Repository Implementation

**Driver:** `neo4j-java-driver` (official, no Spring)  
**Session management:** each method opens a session, executes, closes  
**Node label:** `User`  
**Relationship type:** `FRIENDS_WITH` (undirected stored as two directed edges, or single with no direction enforced)

### Cypher Patterns

| Method | Cypher sketch |
|--------|---------------|
| `create` | `CREATE (u:User {id, name, email, status, interest, abteilung, raum, profilbild, friends})` |
| `getById` | `MATCH (u:User {id: $id}) RETURN u` |
| `update` | `MATCH (u:User {id: $id}) SET u += $props` |
| `delete` | `MATCH (u:User {id: $id}) DETACH DELETE u` |
| `getFriends` | `MATCH (u:User {id: $id})-[:FRIENDS_WITH]->(f:User) RETURN f` |
| `addFriend` | `MATCH (a:User {id: $userId}), (b:User {id: $friendId}) MERGE (a)-[:FRIENDS_WITH]->(b)` |
| `removeFriend` | `MATCH (a:User {id: $userId})-[r:FRIENDS_WITH]->(b:User {id: $friendId}) DELETE r` |
| `getFriendsOfFriends` | `MATCH (u:User {id:$id})-[:FRIENDS_WITH]->(f)-[:FRIENDS_WITH]->(fof) WHERE NOT (u)-[:FRIENDS_WITH]->(fof) AND fof <> u WITH fof, count(f) AS commonCount ORDER BY commonCount DESC RETURN fof` |

`friends` field on `User` node stores IDs as array property for compatibility with existing data class. Relationships are the authoritative source for graph queries.

---

## DB Justification

See `docs/neo4j-justification.md`.

**Summary:** Neo4j chosen because:
- Domain is inherently a social graph (users ↔ friends)
- `getFriendsOfFriends` with common-friend count is a native graph traversal — trivial in Cypher, expensive in SQL (multiple self-joins)
- Relationship queries scale sub-linearly with graph size in Neo4j vs SQL joins that scale with table size
- Schema-flexible nodes suit evolving user profiles

---

## Maven Configuration

- Parent POM: Kotlin plugin + compiler config, Neo4j driver version, kotlin-stdlib managed
- `core/pom.xml`: kotlin-stdlib only
- `neo4j-impl/pom.xml`: depends on `core`, adds `neo4j-java-driver`

Kotlin version: 2.1 (matching existing `.idea/kotlinc.xml`)  
Neo4j driver version: 5.x (latest stable)

---

## Out of Scope

- Connection pooling configuration
- Transaction management beyond single-session
- Error handling beyond what Kotlin null-safety provides
- Tests
