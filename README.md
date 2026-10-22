# MyMemories API

MyMemories is a Spring Boot backend for user accounts and personal memories. The current API implements registration, login, and listing the authenticated user's memories.

## Requirements

- Java 25 (LTS)
- PostgreSQL
- The included Maven wrapper (`mvnw.cmd` on Windows)

## Configuration

The committed `src/main/resources/application.properties` reads database and JWT settings from environment variables. Do not commit real database credentials, JWT secrets, `.env` files, or local application configuration.

| Environment variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL, for example `jdbc:postgresql://localhost:5432/mymemories` |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `JWT_SECRET` | Random secret used to sign JWTs; use a strong value of at least 32 bytes for HS256 |
| `JWT_EXPIRATION` | Token lifetime in milliseconds |
| `PORT` | HTTP server port; defaults to `8080` |

Set these values in your local shell for development and in your hosting provider's secret/environment settings for deployment. Generate a local JWT secret in PowerShell without storing it in the repository:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:JWT_EXPIRATION = "86400000"
```

Set the three `SPRING_DATASOURCE_*` variables to match your local PostgreSQL instance before starting the application. Flyway applies versioned migrations from `src/main/resources/db/migration`; Hibernate validates the resulting schema at startup.

## Run Locally

After PostgreSQL is running and the environment variables are set:

```powershell
.\mvnw.cmd spring-boot:run
```

The API uses the port supplied by the `PORT` environment variable, or `8080` when it is not set.

## API

### Register

`POST /api/auth/register`

Request body:

```json
{
  "username": "your-test-user",
  "email": "your-test-email@example.com",
  "password": "YOUR_DISPOSABLE_TEST_PASSWORD"
}
```

Successful registration returns `201 Created` with the user's id, username, email, and JWT. The service requires a non-empty username, a valid non-empty email, and a password of at least six characters. Usernames and emails must both be unique.

Validation, duplicate-account, and malformed-body errors use a consistent shape:

```json
{
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": {
    "email": "Email must be valid"
  }
}
```

### Login

`POST /api/auth/login`

Request body:

```json
{
  "email": "your-test-email@example.com",
  "password": "YOUR_DISPOSABLE_TEST_PASSWORD"
}
```

Successful login returns `200 OK` with the user's id, username, email, and JWT. Invalid request bodies return `400 Bad Request`; invalid credentials return `401 Unauthorized`, using the same error response shape.

Use a disposable local test password in these examples, not a password used for another account. Replace the placeholder values before sending requests.

### List memories

`GET /api/memories`

This protected endpoint returns only the authenticated user's memories. Send the JWT using `Authorization: Bearer <token>`.

Optional query parameters:

| Parameter | Default | Description |
| --- | --- | --- |
| `q` | — | Case-insensitive literal substring matched against title or content. |
| `page` | `0` | Zero-based page index. |
| `size` | `20` | Number of memories per page. |
| `sort` | `id,desc` | Spring Data sort expression; for example, `sort=title,asc`. |

Example: `GET /api/memories?q=summer&page=0&size=10&sort=title,asc`

The response is a Spring Data page containing a `content` array of `{ "id", "title", "content" }` objects and page metadata such as `totalElements`, `totalPages`, `number`, and `size`.

### Create a memory

`POST /api/memories`

Send a bearer token and a JSON body with a non-blank title (at most 255 characters) and optional content:

```json
{
  "title": "A day at the lake",
  "content": "We went there together."
}
```

Returns `201 Created` with `{ "id", "title", "content" }`. The server assigns the authenticated user as owner; clients cannot set or change ownership.

### Get, update, and delete a memory

- `GET /api/memories/{id}` returns the memory if it belongs to the authenticated user.
- `PUT /api/memories/{id}` replaces its title and content. The title is required; omitted or `null` content clears the content.
- `DELETE /api/memories/{id}` deletes it and returns `204 No Content`.

Each operation returns `404 Not Found` if the memory does not exist or belongs to another user, avoiding disclosure of other users' records. A blank or overlong title returns `400 Bad Request` using the standard validation error shape.

## Test

Run the test suite from PowerShell:

```powershell
.\mvnw.cmd test
```

The tests use an in-memory H2 database and a randomly generated, test-only JWT key. They do not require PostgreSQL or real credentials. The suite checks Spring startup, registration and login responses, malformed bearer-token handling, memory CRUD and owner isolation, validation, search, pagination, and sorting.

To test the authentication endpoints manually, start the app, then send requests with PowerShell. Replace the example values with a disposable local account:

```powershell
$registerBody = @{
    username = "your-test-user"
    email = "your-test-email@example.com"
    password = "YOUR_DISPOSABLE_TEST_PASSWORD"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/auth/register" `
    -ContentType "application/json" `
    -Body $registerBody
```

Then test login using the same email and disposable password:

```powershell
$loginBody = @{
    email = "your-test-email@example.com"
    password = "YOUR_DISPOSABLE_TEST_PASSWORD"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/auth/login" `
    -ContentType "application/json" `
    -Body $loginBody
```

## GitHub and Deployment Checklist

- Store production database credentials and `JWT_SECRET` in the hosting provider's secret manager, not in source files.
- Keep local secrets and local configuration out of Git; inspect `git status` and the staged diff before pushing.
- Set all required environment variables in the hosting service before starting the application.
- Set `CORS_ALLOWED_ORIGINS` to the frontend origin (comma-separated if there is more than one); it is empty by default, so cross-origin browser requests are not allowed until configured.
- Keep the allowed-origin list restricted to frontend origins you control.
- Configure database backups before using the service with persistent user data; Flyway creates and versions the application schema.
- Treat registration, login, memory listing/search, and memory CRUD as the currently available API routes. Categories/tags, uploads, profile, account recovery, and sharing are not implemented.

## Current Project Notes

- Backend source is under `src/main/java/com/group5/MyMemories`.
- Java 25 and Spring Boot are configured in `pom.xml`.
- `application-local.properties` is ignored by `.gitignore`; the tracked `application.properties` contains environment-variable references, not credentials.
- The root `package-lock.json` is empty and untracked; there is no frontend project in this repository yet.