# MyMemories API

MyMemories is a Spring Boot backend for user accounts and personal memories. The API implements registration, login, and authenticated memory CRUD with owner-scoped listing, search, pagination, and sorting.

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

## Deploy to Render

This repository includes a Dockerfile for a Render web service. It builds and tests the Spring Boot application with Java 25 and runs the packaged JAR. The `.dockerignore` excludes local environment files and build output from the image build.

1. Push the backend repository to GitHub. Do not commit `.env` or production credentials.
2. In Render, create **New > Web Service**, connect this repository and branch, and select **Docker** as the runtime. Keep the Dockerfile path as `./Dockerfile`; no separate build or start command is needed.
3. Create the web service in the **same Render region** as your existing Render PostgreSQL database so it can use the database's internal host.
4. In the database's **Connect** page, use the **Internal Database URL** / internal connection details. Set these web-service environment variables:

   | Key | Value |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<internal-host>:<port>/<database-name>` |
   | `SPRING_DATASOURCE_USERNAME` | Database username from Render |
   | `SPRING_DATASOURCE_PASSWORD` | Database password from Render |
   | `JWT_SECRET` | A randomly generated Base64 secret containing at least 32 random bytes |
   | `CORS_ALLOWED_ORIGINS` | Your deployed static site's exact origin, e.g. `https://your-site.onrender.com` |

   Use the host, port, database name, username, and password from Render's internal connection details. The URL must start with `jdbc:postgresql://`; do not paste a `postgres://` or `postgresql://` connection URL unchanged. Keep credentials and the JWT secret in Render's environment settings, not in Git. Render provides `PORT` to the service; the application listens on that port.

   Generate a secret locally in PowerShell and paste its output into Render without saving it in the repository:
   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```

5. Create the service and check its deploy logs. On first startup, Flyway applies the database migration. The service should become healthy after Spring finishes starting.
6. Copy the web service's public URL. Configure your static site's API base URL to that backend URL, then make sure `CORS_ALLOWED_ORIGINS` on the backend matches the static site's exact origin (scheme and hostname, no path). Redeploy/restart the backend after changing environment variables.
7. Smoke-test registration, login, and memory CRUD using a disposable account. Do not use production credentials in test requests.

Do not use the database's external connection URL from the web service; Render's internal database host is intended for services in the same region. If your database is in another region, use the connection method Render documents for that setup and expect external connectivity constraints.

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
| `from` | — | Inclusive earliest `memoryDate` (`YYYY-MM-DD`). |
| `to` | — | Inclusive latest `memoryDate` (`YYYY-MM-DD`). |
| `tag` | — | One or more tag names; memories matching any supplied tag are included. Repeat the parameter, for example `tag=family&tag=japan`. |
| `page` | `0` | Zero-based page index. |
| `size` | `20` | Number of memories per page, capped at `50`. |
| `sort` | `memoryDate,desc`, then `createdAt,desc` | Spring Data sort expression; for example, `sort=title,asc`. |

All provided filters combine with AND, except multiple `tag` values which match ANY of those tags. Example: `GET /api/memories?q=summer&from=2022-01-01&to=2025-12-31&tag=family&tag=japan&page=0&size=10&sort=title,asc`

The response is a Spring Data page containing a `content` array of `{ "id", "title", "content", "memoryDate", "createdAt", "updatedAt", "tags" }` objects and page metadata such as `totalElements`, `totalPages`, `number`, and `size`.

### Create a memory

`POST /api/memories`

Send a bearer token and a JSON body with a non-blank title (at most 150 characters), optional content (at most 10,000 characters), and optional event date:

```json
{
  "title": "A day at the lake",
  "content": "We went there together.",
  "memoryDate": "2025-07-12",
  "tags": ["family", "japan"]
}
```

`memoryDate` is an ISO date (`YYYY-MM-DD`) and defaults to today when omitted. `tags` is optional and can contain up to 10 names, each 1-30 characters. Tag names are trimmed, lowercased, and created automatically for the authenticated user if needed. Returns `201 Created` with the memory fields plus `createdAt` and `updatedAt` ISO timestamps and normalized tags. The server assigns the authenticated user as owner; clients cannot set or change ownership.

### Get, update, and delete a memory

- `GET /api/memories/{id}` returns the memory if it belongs to the authenticated user.
- `PUT /api/memories/{id}` replaces its title, content, and event date. The title is required; omitted or `null` content clears the content; omitted event date defaults to today.
- `DELETE /api/memories/{id}` deletes it and returns `204 No Content`.

Each operation returns `404 Not Found` if the memory does not exist or belongs to another user, avoiding disclosure of other users' records. A blank or overlong title, or content exceeding 10,000 characters, returns `400 Bad Request` using the standard validation error shape.

### Current user

`GET /api/users/me` returns the authenticated user's `id`, `username`, and `email`. It does not expose password data.

### Tags

`GET /api/tags` returns the authenticated user's tags, sorted by name, with `{ "name", "memoryCount" }` fields. Tags are private to their owner. Updating a memory replaces its tag list; deleting a memory leaves its tags intact, reporting zero for tags with no remaining memories.

### On this day and resurfacing

Pass the client's local date explicitly so server timezone differences do not affect the result:

- `GET /api/memories/on-this-day?date=2025-02-28` returns prior-year memories with a matching month/day, grouped by year in descending order. In a non-leap year, February 28 also includes memories from February 29 in past leap years.
- `GET /api/memories/resurface?date=2025-02-28` returns one memory, preferring an on-this-day match. If there is no match, it selects a random memory at least one year old. It returns `204 No Content` if no memory qualifies and avoids repeating the prior day's surfaced memory when another choice is available.

Resurfacing is private and in-app only; it does not send notifications.

### Resurfacing settings

- `GET /api/users/me/settings` returns `{ "resurfaceFrequency": "DAILY" }` for a new account.
- `PUT /api/users/me/settings` accepts `{"resurfaceFrequency":"DAILY"}`, `{"resurfaceFrequency":"WEEKLY"}`, or `{"resurfaceFrequency":"OFF"}`.
- `DAILY` allows one stable surfaced memory per client-provided date. `WEEKLY` allows one surfaced memory every seven days; requests before the seven-day interval return `204 No Content`. `OFF` always returns `204 No Content` from the resurface endpoint.

Settings apply only to in-app resurfacing; they do not configure email or push notifications.

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