# User Repository Design

## Overview

Kotlin repository interface that encapsulates all user-related data access. No database implementation — interface only. Consumers program against this contract; implementations (DB, in-memory, mock) are supplied externally.

## Domain Model

### `Status` (enum)

```kotlin
enum class Status {
    ONLINE, OFFLINE, BUSY
}
```

### `User` (data class)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | UUID string, caller-provided |
| `name` | `String` | |
| `email` | `String` | |
| `status` | `Status` | |
| `interest` | `String` | |
| `abteilung` | `String` | Department |
| `raum` | `String` | Room |
| `profilbild` | `String?` | Optional URL or file path |
| `friends` | `List<String>` | List of friend user IDs (not nested User objects — avoids circular references) |

## Repository Interface

### `UserRepository`

| Method | Signature | Behavior |
|--------|-----------|----------|
| Create | `fun create(user: User)` | Persists new user |
| Read | `fun getById(id: String): User?` | Returns null if not found |
| Update | `fun update(user: User)` | Locates record by `user.id`, replaces all fields |
| Delete | `fun delete(id: String)` | Removes user by ID |
| Get friends | `fun getFriends(userId: String): List<User>` | Returns resolved User objects for all friend IDs |
| Add friend | `fun addFriend(userId: String, friendId: String)` | Adds `friendId` to user's friend list |
| Remove friend | `fun removeFriend(userId: String, friendId: String)` | Removes `friendId` from user's friend list |
| Friends-of-friends | `fun getFriendsOfFriends(userId: String): List<User>` | Returns users who are friends of the user's friends, excluding direct friends and self, sorted descending by number of common friends |

## Key Design Decisions

- **Friends as IDs** — `User.friends` stores `List<String>` (IDs), not `List<User>`. Avoids circular object graphs and makes serialization straightforward.
- **Single interface** — all operations on one `UserRepository`. Simple to implement and inject.
- **Null on miss** — `getById` returns `User?`; all other methods return `Unit` where no meaningful value exists.
- **Update by object** — `update(user: User)` uses the ID embedded in the object to locate the record. Caller constructs the full updated User.
- **Friends-of-friends ordering** — sorted descending by common friend count; ties have unspecified order.
