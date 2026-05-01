# Testing Design

**Date:** 2026-04-30
**Scope:** Unit tests for UserService (core) + integration tests for Neo4jUserRepository (neo4j-impl)

---

## Overview

Two test layers:
1. **Unit tests** (`core`) — verify `UserService` delegates correctly to `UserRepository` using MockK
2. **Integration tests** (`neo4j-impl`) — verify `Neo4jUserRepository` Cypher queries against a real Neo4j instance via Testcontainers

---

## File Structure

```
core/src/test/kotlin/service/
    UserServiceTest.kt

neo4j-impl/src/test/kotlin/repository/
    Neo4jUserRepositoryIT.kt
```

---

## Dependencies

### core/pom.xml additions

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.mockk</groupId>
    <artifactId>mockk-jvm</artifactId>
    <version>${mockk.version}</version>
    <scope>test</scope>
</dependency>
```

### neo4j-impl/pom.xml additions

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>neo4j</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
```

### Parent POM version properties

```
junit.version=5.11.0
mockk.version=1.13.13
testcontainers.version=1.20.4
```

Both child POMs also need the maven-surefire-plugin (JUnit 5 runner) configured in parent pluginManagement.

---

## UserServiceTest (unit)

**Location:** `core/src/test/kotlin/service/UserServiceTest.kt`
**Framework:** JUnit 5 + MockK
**Strategy:** mock `UserRepository`, verify each `UserService` method calls the correct repository method with correct arguments

**Tests (one per UserService method):**

| Test | Verifies |
|------|----------|
| `createUser delegates to repository` | `repository.create(user)` called once |
| `getUser delegates to repository` | `repository.getById(id)` called, result returned |
| `updateUser delegates to repository` | `repository.update(user)` called once |
| `deleteUser delegates to repository` | `repository.delete(id)` called once |
| `addFriend delegates to repository` | `repository.addFriend(userId, friendId)` called once |
| `removeFriend delegates to repository` | `repository.removeFriend(userId, friendId)` called once |
| `getFriends delegates to repository` | `repository.getFriends(userId)` called, list returned |
| `suggestFriends delegates to getFriendsOfFriends` | `repository.getFriendsOfFriends(userId)` called, list returned |

---

## Neo4jUserRepositoryIT (integration)

**Location:** `neo4j-impl/src/test/kotlin/repository/Neo4jUserRepositoryIT.kt`
**Framework:** JUnit 5 + Testcontainers (`neo4j` module)
**Strategy:** `@TestInstance(PER_CLASS)` — one Neo4j container for all tests, `@BeforeEach` deletes all nodes between tests

**Container setup:** `Neo4jContainer("neo4j:5")` with `withoutAuthentication()`

**Tests:**

| Test | Verifies |
|------|----------|
| `create and getById roundtrip` | node created, all fields readable back |
| `getById returns null for unknown id` | missing node returns null |
| `update changes fields` | `update()` overwrites all mutable fields |
| `delete removes node` | `getById` returns null after delete |
| `addFriend creates symmetric relationship` | both `a.getFriends()` and `b.getFriends()` include each other |
| `addFriend updates friends property on both nodes` | `friends` array property synced on both nodes |
| `removeFriend removes relationship` | `getFriends` no longer includes removed friend |
| `getFriendsOfFriends returns correct users` | 3-user chain: A→B→C, A's fof = C |
| `getFriendsOfFriends orders by common friends` | user with more common friends ranked first |
| `nullable profilbild stored and retrieved correctly` | null profilbild round-trips as null |

---

## Build Configuration

Both child POMs need the maven-surefire-plugin activated for JUnit 5 (surefire 3.x auto-detects JUnit Platform if the plugin is present). Add to parent `pluginManagement`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
</plugin>
```

And in each child POM's `<build><plugins>`, activate it (no version needed, managed by parent).

Also add the kotlin-maven-plugin `test-compile` execution to the parent `pluginManagement` and each child POM, so Kotlin test sources are compiled.

---

## Out of Scope

- Mocking the Neo4j driver at unit level (Testcontainers gives more confidence)
- Performance / load tests
- `close()` lifecycle tests
