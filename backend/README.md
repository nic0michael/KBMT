# TOGAFmate Backend

Spring Boot REST API using Java 21, Spring Data JPA, Spring Security, PostgreSQL, and Gradle Kotlin DSL. Swagger documents the API. User passwords are stored as BCrypt hashes.

## Run

For local development, run `gradlew.bat bootRun` on Windows or `./gradlew bootRun` on macOS/Linux. The Gradle `bootRun` task activates the local Spring profile and reads its database connection from the ignored `backend/application-local.properties` file. Update that local-only file with your PostgreSQL credentials as needed. Do not commit it. The file stays outside packaged resources, and Docker Compose deployment continues to supply database credentials through its runtime environment. The default JPA schema mode is `update`; set `JPA_DDL_AUTO` to change it.

## Test

Run `gradlew.bat test` on Windows or `./gradlew test` on macOS/Linux. JaCoCo reports are written to `build/reports/jacoco/test/`.

## API testing

When running the backend locally, use Swagger UI at <http://localhost:8999/swagger-ui/index.html> or the OpenAPI document at <http://localhost:8999/v3/api-docs>.

The endpoints include `GET /api/health`, `GET` and `POST /api/knowledge-bases`, `PUT /api/knowledge-bases/{id}`, `POST /api/users/register`, `POST /api/users/login`, `GET /api/users`, and user update and permission operations. Registration creates accounts with `NO_ACCESS`; login verifies credentials and returns the user's permissions without the password.

The logon lookup uses a named JPQL parameter for `userID`; passwords are BCrypt-encoded and compared outside the query. Raw passwords are never queried or returned.

The POST endpoint requires `title`, `folderNameVersion`, `version`, and `contentType`. Content type must be `Free`, `Open-source`, or `Copyrighted`. `author`, `emailAddress`, `tableOfContents`, and `description` are optional; a supplied email address must be valid. The folder identifier must be a safe, unique folder name.

The Knowledge Base metadata table is managed by Spring Data JPA. Tests use an in-memory H2 database, so PostgreSQL does not need to be running to execute `gradlew.bat test`.

User permissions are stored as `USER_ACCESS`, `ADMINISTRATOR_USER_ACCESS`, or `NO_ACCESS`. An administrator can change a user's ID and optionally their password with `PUT /api/users/{userID}` and add or remove a permission with `PUT` or `DELETE /api/users/{userID}/permissions`, using a JSON body such as `{"permission":"USER_ACCESS"}`. Permission and account changes require HTTP Basic authentication with `ADMINISTRATOR_USER_ACCESS`. Knowledge Base reads require `USER_ACCESS` or `ADMINISTRATOR_USER_ACCESS`; Knowledge Base creation and updates require `ADMINISTRATOR_USER_ACCESS`. HTTP Basic credentials must be sent with protected requests, so deploy behind HTTPS.

Set `ADMIN_USER` and `ADMIN_USER_PASSWORD` in the local environment for the configured administrator. At startup, the backend creates or reconciles this account, ensures `USER_ACCESS` and `ADMINISTRATOR_USER_ACCESS`, and BCrypt-encodes the configured password. API changes to this account's ID or password must also be applied to the environment before restarting. Do not commit these environment values.

## CORS

The API allows the Angular development origin `http://localhost:4200` by default. Set `APP_CORS_ALLOWED_ORIGINS` to a comma-separated list of exact frontend origins when running in another environment.
