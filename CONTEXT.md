# MyMemories Frontend Context

## Project summary

MyMemories is intended to let users store and organize personal memories. The backend implements account registration, login, and authenticated, owner-scoped memory create/read/update/delete operations with search, pagination, and sorting. This repository contains no frontend.

## Tech stack

- Java 25 (LTS); Spring Boot `3.5.9`
- Spring MVC (`spring-boot-starter-web`)
- Spring Security and Spring Data JPA
- PostgreSQL at runtime; H2 for tests
- JJWT `0.11.5` for JWTs; BCrypt for password hashes
- Lombok; Maven Wrapper (Maven `3.9.11`)

## Data models

Only `User` and `Memory` are JPA entities. Flyway migration `V1__create_users_and_memories.sql` creates their tables; Hibernate validates the schema at startup.

### User (`users`)

- `id`: `Long`, generated identity primary key.
- `username`: `String`, required and unique in the database.
- `password`: `String`, required; signup stores a BCrypt hash.
- `email`: `String`, required and unique in the database.
- `resurfaceFrequency`: `DAILY`, `WEEKLY`, or `OFF`; defaults to `DAILY` and controls in-app resurfacing only.
- `memories`: `List<Memory>`, one-to-many inverse relationship; initialized to an empty list. Cascade-all and orphan removal are configured.

Signup validates non-blank username/email, email format, and password length >= 6, and explicitly checks username and email uniqueness.

### Memory (`memories`)

- `id`: `Long`, generated identity primary key.
- `title`: `String`, required; at most 150 characters.
- `content`: `String`, optional; at most 10,000 characters and explicitly mapped to SQL `TEXT`.
- `memoryDate`: `LocalDate`, required; date the event happened, defaulting to today when omitted.
- `createdAt` / `updatedAt`: `LocalDateTime` values maintained when the memory is saved and edited.
- `tags`: a set of owner-scoped `Tag` entities linked through `memory_tags`.
- `user`: `User`, required many-to-one relationship via `user_id`.

An index on `(user_id, memory_date)` supports owner-scoped timeline queries. Authenticated users can create, list, retrieve, update, and delete only their own memories. Tags belong to one user, are stored trimmed and lowercase, are unique per owner, and are limited to 30 characters and 10 tags per memory. `Category` remains an empty class; photos, sharing, visibility, and dedicated search fields are not modeled.

## API reference

The mapped routes include registration, login, and the authenticated memory list and CRUD operations.

| Method and URL | Auth | Request | Success response |
|---|---|---|---|
| `POST /api/auth/register` | No | `{"username":"sam","email":"sam@example.com","password":"example-password"}` | `201`: `{"id":1,"username":"sam","email":"sam@example.com","message":null,"token":"<jwt>"}` |
| `POST /api/auth/login` | No | `{"email":"sam@example.com","password":"example-password"}` | `200`: `{"id":1,"username":"sam","email":"sam@example.com","token":"<jwt>"}` |
| `GET /api/memories` | Bearer JWT | Optional `q`, `page`, `size`, and `sort` query parameters | `200`: Spring Data page with `content` records (`id`, `title`, `content`) and page metadata |

| Method and URL | Auth | Request | Success response |
|---|---|---|---|
| `POST /api/memories` | Yes | `{"title":"A memory","content":"Optional details"}` | `201`: memory record (`id`, `title`, `content`) |
| `GET /api/memories/{id}` | Yes | — | `200`: memory record; `404` if absent or owned by another user |
| `PUT /api/memories/{id}` | Yes | `{"title":"Updated title","content":"Updated details"}` | `200`: updated record; `404` if absent or owned by another user |
| `DELETE /api/memories/{id}` | Yes | — | `204`; `404` if absent or owned by another user |

The `id` and token values are generated. Request DTOs use Bean Validation: registration requires a non-blank username, valid non-blank email, and password of at least six characters; login requires a valid non-blank email and non-blank password. Duplicate usernames and emails return `400`; malformed or null request bodies return `400`. Errors use `{"message":"...","fieldErrors":{"field":"..."}}`; the HTTP status communicates the error status. Invalid login credentials and invalid bearer tokens return `401` with this shape.

Memory collection operations are `GET /api/memories` (list/filter) and `POST /api/memories` (create); item operations are `GET`, `PUT`, and `DELETE /api/memories/{id}`. All memory operations are scoped to the authenticated user; requests for another user's memory return `404`. Titles must be non-blank and at most 150 characters; content is optional and at most 10,000 characters. `memoryDate` is an optional ISO date in requests and defaults to today; responses include `memoryDate`, `createdAt`, `updatedAt`, and normalized `tags`. A `tags` request array is optional, limited to 10 values; values are trimmed, lowercased, and must be 1-30 characters. Missing tags are created for the authenticated owner. `PUT` replaces title, content, event date, and tags; omitted or null content clears it, and omitted tags clear the memory's tags. Deleting a memory does not delete its tags. `DELETE` returns no response body. `GET /api/memories` supports `q` (case-insensitive title/content substring), inclusive `from` and `to` dates, and repeated `tag` parameters (match any supplied tag); filters combine with AND and execute as a database query scoped to the owner. Pagination uses zero-based `page` (default `0`) and `size` (default `20`, maximum `50`). Default sorting is `memoryDate,desc` then `createdAt,desc`; custom Spring Data sort syntax is supported. `GET /api/tags` returns the authenticated user's tags and each tag's memory count; tags with no attached memories remain listed with count zero. `GET /api/users/me` returns only the authenticated user's id, username, and email. Logout, password-reset, upload, category, and sharing endpoints are not implemented.

### Tags API and memory fields

Memory create/update requests may include `"tags":["family","japan"]`; memory responses include normalized tag names. Tag names are trimmed, lowercased, and created automatically for the authenticated user. Names must be 1-30 characters, with at most 10 tags per memory. A PUT replaces the tag list; omitting tags clears it. `GET /api/tags` returns the authenticated user's `{name, memoryCount}` entries, including unused tags with a zero count. Tags are not deleted with memories and are never visible to another user.

### On-this-day and resurfacing

- `GET /api/memories/on-this-day?date=YYYY-MM-DD` accepts the client's local date and returns earlier-year memories grouped by year. February 29 memories also appear on February 28 in non-leap years.
- `GET /api/memories/resurface?date=YYYY-MM-DD` prefers an on-this-day memory; otherwise it returns one random memory dated at least a year before the supplied date. It returns `204 No Content` if none qualify and avoids repeating the previous day's memory when an alternative exists.
- Both endpoints require authentication and return only the current user's memories. Resurfacing is in-app only and sends no notifications.

### Resurfacing settings API

- `GET /api/users/me/settings` returns `{"resurfaceFrequency":"DAILY"}` by default.
- `PUT /api/users/me/settings` accepts `{"resurfaceFrequency":"DAILY"}`, `{"resurfaceFrequency":"WEEKLY"}`, or `{"resurfaceFrequency":"OFF"}`.
- `WEEKLY` permits one resurfaced memory every seven days; requests during the cooldown and all requests while `OFF` return `204` from `/api/memories/resurface`.

## Auth flow

1. Signup or login is called without a token. Signup hashes the password and persists a user; login checks the submitted password against the stored BCrypt hash.
2. Both successful flows return a JWT. It is HS256-signed, has the user email as subject, and includes issue and expiry timestamps.
3. JWT lifetime is configured by `JWT_EXPIRATION`, default `86400000` ms (24 hours). `JWT_SECRET` is required.
4. For non-auth paths, the JWT filter reads the bearer token, validates its signature/expiry, looks up the user by email, and sets that user as the authenticated principal.
5. For protected requests, send the JWT in the HTTP `Authorization` header using the `Bearer` scheme. JSON requests should use `Content-Type: application/json`.

Spring Security permits `/api/auth/**` and requires authentication for every other path. The JWT filter skips `/api/auth/`. Security sessions are stateless; no cookies are issued/read. Logout, revocation, refresh tokens, password reset, roles, and role-based permissions are not implemented.

## User flows

- **Signup:** submit username/email/password; on `201`, show success and use the returned user data/token as appropriate. Backend checks are described above.
- **Login:** submit email/password; on `200`, use returned user data/token; invalid credentials yield `401`.
- **Memory list/search:** authenticated users can list their own memories using `GET /api/memories`, with optional case-insensitive title/content search, pagination, and sorting by event date and save time by default.
- **Memory create/view/edit/delete:** authenticated users can create, retrieve, replace, and delete their own memories. Each record has a required title, optional content, event date, save/edit timestamps, and private owner-scoped tags.
- **Tags:** `GET /api/tags` lists the authenticated user's normalized tags and memory counts.
- **Profile:** `GET /api/users/me` returns the authenticated user's id, username, and email without password data.
- **Photos, categories, and sharing:** not implemented.

No frontend behavior is specified in this backend; screen transitions and token persistence are decisions for the frontend.

## Error handling

- Validation and duplicate-account errors return `400` with `message` and `fieldErrors`.
- Malformed and null request bodies return `400` using the same error shape.
- Invalid login credentials, bearer tokens, or missing authentication return `401` using the same error shape.
- Unexpected database and framework errors are not assigned a custom API contract; clients should still handle unrecognized responses defensively.

## Known issues

- CORS allowed origins default to empty. Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin(s), comma-separated. Configured methods are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; allowed headers are `Authorization`, `Content-Type`. Credentials are not enabled.
- Upload, account recovery, logout/revocation, categories, and sharing APIs are not implemented. Tag, profile, and resurfacing settings endpoints are implemented.
- No API documentation/OpenAPI setup or schema migration beyond the initial `users` and `memories` migration is present.
- Password minimum is six characters; no email-format check or signup rate limiting was found.
- No logout/revocation, refresh, reset, verification, role/permission, or account-lockout features.
- JJWT is pinned to `0.11.5`, and `JwtUtil` uses deprecated JJWT API methods.
- Server port uses `PORT` when provided and defaults to `8080`.
- No `TODO`, `FIXME`, `XXX`, or `HACK` markers were found in the searched project files.

## Suggested screens for the frontend

These are frontend suggestions, not existing backend features:

1. Sign up and log in forms, with inline field errors for the backend’s current messages.
2. An authenticated memory list/dashboard can now use `GET /api/memories`, including its search and page metadata.
3. Memory creation, detail, editing, and deletion screens can use the CRUD endpoints described above.
4. Future account recovery, upload, categories, and sharing screens only if corresponding backend features are added.
