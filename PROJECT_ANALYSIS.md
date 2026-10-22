# MyMemories Project Analysis

**Scope:** Snapshot of the current backend behavior. Update this report when API contracts change; the repository's README is the primary setup and deployment guide.

## 1. Project Purpose

The stated goal is a secure backend where users can store, organize, search, and protect personal memories. The API supports registration and login with JWTs, plus owner-scoped memory create/read/update/delete, search, pagination, and sorting. Category management and uploads are not implemented.

There is no frontend in this repository. The root `package-lock.json` is empty and does not represent a working frontend project.

## 2. Tech Stack

| Area | Technology | Version / notes |
| --- | --- | --- |
| Language | Java | Project targets Java 25 (LTS); use a Java 25 runtime for deployment. |
| Framework | Spring Boot | Parent is declared as the stable `3.5.9` release. |
| Web/API | Spring MVC | `spring-boot-starter-web`; servlet-based REST controllers. |
| Authentication | Spring Security | `spring-boot-starter-security`; custom bearer-token filter. |
| Persistence | Spring Data JPA / Hibernate | `spring-boot-starter-data-jpa`; ORM version managed by Spring Boot. |
| Production database driver | PostgreSQL | JDBC driver is runtime-scoped; exact version managed by Spring Boot. |
| Test database | H2 | Test-scoped dependency; Maven resolved H2 `2.3.232` in the recorded test run. |
| JWT | JJWT | `jjwt-api`, `jjwt-impl`, and `jjwt-jackson` at `0.11.5`. |
| Boilerplate | Lombok | Declared once in `pom.xml`; entity accessors use `@Getter` and `@Setter`. |
| Schema migrations | Flyway | Versioned migrations are under `src/main/resources/db/migration`; Hibernate validates the schema at startup. |
| Build | Maven Wrapper | Wrapper scripts are present; use `mvnw.cmd` on Windows and `./mvnw` on Unix-like systems. |
| Tests | JUnit 5, Spring Boot Test, MockMvc | Test dependencies come from `spring-boot-starter-test`; `spring-security-test` is also declared. |

Spring snapshot repositories are not configured.

## 3. Folder Structure

```text
MyMemories/
|-- .mvn/wrapper/                 Maven Wrapper configuration
|-- mvnw, mvnw.cmd                Maven Wrapper launchers
|-- pom.xml                       Maven dependencies, Java target, plugins
|-- README.md                     Setup, current API, test, and deployment notes
|-- PROJECT_ANALYSIS.md           This report
|-- package-lock.json             Empty/untracked npm lockfile; no frontend package
|-- src/
|   |-- main/
|   |   |-- java/com/group5/MyMemories/
|   |   |   |-- MyMemoriesApplication.java    Spring Boot entry point
|   |   |   |-- config/SecurityConfig.java    Security filter, password encoder, CORS
|   |   |   |-- controller/AuthController.java Registration and login routes
|   |   |   |-- controller/UserController.java Memory listing and CRUD routes
|   |   |   |-- dto/MemoryDTO.java             Memory response shape
|   |   |   |-- dto/request/                   LoginRequest, RegisterRequest
|   |   |   |-- dto/response/                  LoginResponse, RegisterResponse
|   |   |   |-- entity/                        User, Memory, empty Category placeholder
|   |   |   |-- exception/                     API exception and global error handler
|   |   |   |-- mapper/userMapper.java         Empty mapper placeholder
|   |   |   |-- repositories/                  UserRepository and MemoryRepository
|   |   |   |-- service/AuthService.java       Authentication service interface
|   |   |   |-- service/impl/AuthServiceImpl.java Registration/login logic
|   |   |   `-- utils/                         JWT utility and request filter
|   |   `-- resources/
|   |       |-- application.properties         Environment-backed runtime config
|   |       `-- META-INF/additional-spring-configuration-metadata.json
|   `-- test/java/com/group5/MyMemories/
|       `-- MyMemoriesApplicationTests.java    H2-backed context and auth tests
`-- target/                       Generated Maven output; should not be committed
```

## 4. Data Models

Only `User` and `Memory` are JPA entities. No category or image entity/table is implemented.

| Entity/table | Fields | Relationships and constraints |
| --- | --- | --- |
| `User` / `users` | `id` (`Long`, generated identity primary key), `username` (`String`), `password` (`String`), `email` (`String`), `memories` (list) | Username and email are non-null and unique. `User` has a one-to-many relationship to `Memory`, mapped by `Memory.user`, with cascade-all and orphan removal. Password is stored as a BCrypt hash by the registration service. |
| `Memory` / `memories` | `id` (`Long`, generated identity primary key), `title` (`String`), `content` (`String`, mapped to SQL `TEXT`), `user` (`User`) | Many-to-one to `User`; `user_id` is non-null. `title` is non-null. `content` is nullable by the current mapping. |
| `Category` | None | Plain empty Java class, not annotated as an entity and has no table or relationship. |

There are no explicit date/time, image URL, visibility/privacy, category, or audit fields. The `User` and `Memory` entities use Lombok `@Getter` and `@Setter`, avoiding `@Data`-generated `toString`, `equals`, and `hashCode` methods over JPA relationships.

## 5. API Endpoints

Request DTOs use Bean Validation for required fields, email format, the registration password minimum, and memory title constraints.

| Method | Route | Request body | Response | Auth required? |
| --- | --- | --- | --- | --- |
| `POST` | `/api/auth/register` | `{"username":"...","email":"...","password":"..."}` | `201`: `{id, username, email, token}`. Validation and duplicate-account errors return `400` in the common error shape. | No |
| `POST` | `/api/auth/login` | `{"email":"...","password":"..."}` | `200`: `{id, username, email, token}`. Invalid credentials return `401` in the common error shape. | No |
| `GET` | `/api/memories` | Optional `q`, `page`, `size`, `sort` query parameters | `200`: page of current user's `{id, title, content}` records. | Yes |
| `POST` | `/api/memories` | `{"title":"...","content":"..."}` | `201`: `{id, title, content}`; owner comes from authentication. | Yes |
| `GET` | `/api/memories/{id}` | — | `200`: `{id, title, content}`; `404` if absent or non-owned. | Yes |
| `PUT` | `/api/memories/{id}` | `{"title":"...","content":"..."}` | `200`: updated record; `404` if absent or non-owned. | Yes |
| `DELETE` | `/api/memories/{id}` | — | `204`; `404` if absent or non-owned. | Yes |

Memory titles must be non-blank and no longer than 255 characters; content is optional. PUT replaces the title and content, and missing/null content clears it. Memory queries constrain results to the authenticated user's id; attempts to access another user's record return `404`.

Validation, malformed/null request bodies, and duplicate accounts return `{"status":number,"message":"...","fieldErrors":{"field":"..."}}`.

## 6. Authentication and Authorization

1. Registration validates username, email, and password; checks username and email uniqueness; hashes the password with BCrypt; saves the user; and returns a JWT.
2. Login looks up the user by email and compares the submitted password against the BCrypt hash. On success, it returns a JWT; on failure, it returns `401`.
3. JWT subject is the user's email. The token includes issue and expiry timestamps and is signed with HS256 using the configured JWT secret.
4. Requests outside `/api/auth/` are inspected by `JwtAuthenticationFilter`. A bearer token is parsed, its email is used to load a user, and the token subject is validated against that user's email. Malformed/invalid tokens return `401`.
5. `SecurityConfig` permits `/api/auth/**` and requires authentication for all other requests.

There are no roles or role claims. Spring Security uses a stateless session policy, and each memory endpoint scopes reads, updates, and deletes to the authenticated user's id. There is no refresh-token, revocation, password-reset, email-verification, or rate-limiting flow.

## 7. Business Workflow

### Implemented workflow

1. A client posts registration details to `/api/auth/register`.
2. The server validates required values, email format, and minimum password length; checks username and email uniqueness; BCrypt-hashes the password; and saves the user.
3. The server returns a JWT with the new user's details.
4. The client can later post email/password to `/api/auth/login` and receive a new JWT.
5. For a future protected request, the client must send `Authorization: Bearer <token>`.

### Memory workflow

- Create with `POST /api/memories`; the server derives the owner from the authenticated principal.
- List/search with `GET /api/memories`, or retrieve one owned record with `GET /api/memories/{id}`.
- Replace with `PUT /api/memories/{id}` or delete with `DELETE /api/memories/{id}`. Foreign or missing records return `404`.

### Additional product features not implemented

Uploading images and categorizing memories are not implemented.

## 8. Configuration and Environment

There is no `.env` file loader. Spring reads operating-system environment variables or hosting-platform environment settings via `src/main/resources/application.properties`.

| Variable | Required? | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Yes at application runtime | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://localhost:5432/mymemories` |
| `SPRING_DATASOURCE_USERNAME` | Yes at application runtime | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Yes at application runtime | PostgreSQL password |
| `JWT_SECRET` | Yes at application runtime | Signing key. The current JJWT `signWith(SignatureAlgorithm, String)` API treats the string as Base64-encoded key material; provide a Base64 encoding of at least 32 random bytes for HS256. Keep it secret and identical across instances. |
| `JWT_EXPIRATION` | No | Token lifetime in milliseconds; defaults to `86400000` (24 hours). |
| `CORS_ALLOWED_ORIGINS` | No for same-origin use; needed for browser frontend on a different origin | Comma-separated exact allowed origins. Defaults to empty, which blocks cross-origin browser requests. |

Production DB credentials and JWT secrets belong in the hosting provider's secret manager, not in Git. `application-local.properties` is ignored by `.gitignore`; the tracked application config contains placeholders rather than secret values.

The production database is PostgreSQL. Flyway applies checked-in migrations and Hibernate uses `ddl-auto=validate` to check the resulting schema. The test suite overrides the database with in-memory H2 and a newly generated random test JWT key, so tests do not require PostgreSQL credentials.

Run tests from the repository root:

```powershell
.\mvnw.cmd test
```

To run the service locally, start PostgreSQL, set the required variables in the current PowerShell session, then run the wrapper. Example variable names and a generated local key:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/mymemories"
$env:SPRING_DATASOURCE_USERNAME = "your-local-db-user"
$env:SPRING_DATASOURCE_PASSWORD = "your-local-db-password"
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:JWT_EXPIRATION = "86400000"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:3000"
.\mvnw.cmd spring-boot:run
```

Do not use the sample placeholder DB values as real credentials. The server binds to the `PORT` environment variable when present and otherwise uses `8080`.

## 9. Incomplete or Broken Parts

### Incomplete / unused scaffolding

- `Category` and `userMapper` are currently unused scaffolding.
- There is no category service/API or image storage/migration.
- There are no upload/storage libraries or image metadata fields.
- `package-lock.json` is empty and untracked; there is no frontend source or `package.json`.
- Spring configuration metadata describes JWT properties with generic descriptions and lists expiration as a string, though it is injected into a `long` field.

### Validation and error handling gaps

- Registration only enforces a six-character minimum password; no maximum field lengths or stronger password policy are defined.
- Duplicate-email check and insert are separate operations; concurrent registrations can still collide at the unique constraint.
- Bean Validation, malformed/null request handling, duplicate-account errors, and authentication failures use a common error response. Unexpected framework/database errors do not have a custom response contract.
- No category filtering or API versioning contract is defined.

### Security / deployment risks

- Password minimum length of six is weak for a public service; there is no throttling or lockout on login attempts.
- JWTs have no revocation or refresh mechanism. Users cannot invalidate a stolen token before expiration.
- There is no role model; memory operations are restricted to the authenticated owner.
- CSRF is disabled and session management is stateless, appropriate only while authentication remains bearer-token based and not cookie-based.
- JWT signing/parsing uses deprecated JJWT overloads; newer `SecretKey` APIs are preferable. Validate the decoded key length and fail fast on malformed configuration.
- CORS is restricted by default, which is secure, but the deployed frontend's exact origin must be configured.
- No health/readiness endpoint, structured logging policy, or deployment pipeline is present.

The automated integration suite covers context startup, registration/login, bearer-token handling, memory CRUD, owner isolation, validation, search, pagination, and sorting. It does not replace a production deployment and integration checklist.

## 10. What's Missing Before the Frontend Can Be Built

The authentication screens can be started against the current contract, but a useful memory-management frontend needs these backend decisions and features first:

1. Configure production environment variables and the exact frontend origin before deployment.
2. Decide whether memory timestamps, privacy/visibility, or location/date metadata are required; these are not currently modeled.
3. Implement categories/tags only if they are part of the product; define their ownership and memory relationship.
4. Decide image upload architecture (object storage vs. database), upload limits, accepted MIME types, image metadata, and authenticated access to stored images.
5. Extend the common validation/error contract as additional endpoints and request DTOs are introduced.
6. Add a current-user/profile endpoint and decide how frontend token expiry, logout, and future refresh/revocation should work.
7. Document the deployed API base URL and frontend configuration.
8. Configure deployment environment variables, including `PORT`, and plan database backups for the migrated schema.
9. Add deployment-environment and CORS smoke checks for the actual hosting service and frontend origin.

Registration, login, and memory management screens can use the implemented endpoints. Uploads, categories/tags, profile, recovery, sharing, and token lifecycle features still require product decisions and backend work if they are in scope.