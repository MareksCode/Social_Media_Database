# User Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the Kotlin domain model and `UserRepository` interface that encapsulates all user data access operations.

**Architecture:** Three files — `Status` enum, `User` data class, and `UserRepository` interface. No implementation, no database. Friends stored as `List<String>` (IDs) to avoid circular object graphs.

**Tech Stack:** Kotlin, IntelliJ IDEA project (no build tool)

---

### Task 1: `Status` enum

**Files:**
- Create: `src/model/Status.kt`

- [ ] **Step 1: Create the file**

```kotlin
package model

enum class Status {
    ONLINE, OFFLINE, BUSY
}
```

- [ ] **Step 2: Commit**

```bash
git add src/model/Status.kt
git commit -m "feat: add Status enum"
```

---

### Task 2: `User` data class

**Files:**
- Create: `src/model/User.kt`

- [ ] **Step 1: Create the file**

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

- [ ] **Step 2: Commit**

```bash
git add src/model/User.kt
git commit -m "feat: add User data class"
```

---

### Task 3: `UserRepository` interface

**Files:**
- Create: `src/repository/UserRepository.kt`

- [ ] **Step 1: Create the file**

```kotlin
package repository

import model.User

interface UserRepository {

    /** Persists a new user. */
    fun create(user: User)

    /** Returns the user with [id], or null if not found. */
    fun getById(id: String): User?

    /**
     * Replaces all fields of the existing user whose id matches [user.id].
     */
    fun update(user: User)

    /** Removes the user with [id]. */
    fun delete(id: String)

    /** Returns resolved User objects for every friend ID in the user's friends list. */
    fun getFriends(userId: String): List<User>

    /** Adds [friendId] to the friend list of the user with [userId]. */
    fun addFriend(userId: String, friendId: String)

    /** Removes [friendId] from the friend list of the user with [userId]. */
    fun removeFriend(userId: String, friendId: String)

    /**
     * Returns users who are friends of the user's friends,
     * excluding direct friends and the user themselves,
     * sorted descending by number of common friends.
     */
    fun getFriendsOfFriends(userId: String): List<User>
}
```

- [ ] **Step 2: Commit**

```bash
git add src/repository/UserRepository.kt
git commit -m "feat: add UserRepository interface"
```
