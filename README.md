# B-ting Backend

B-ting backend is a Spring Boot API server. The current development branch focuses on the user domain: user sign-up, sign-in style lookup, duplicate email validation, and basic global API error handling.

## Tech Stack

| Area | Stack |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 |
| Web | Spring Web MVC |
| Validation | Jakarta Validation |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| Test | JUnit 6, Mockito, Spring Boot Test, Testcontainers, JaCoCo |
| Code Quality | SonarQube, SonarScanner for Gradle |
| Formatting | Spotless, Google Java Format |
| Local Infra | Docker Compose, PostgreSQL 16 Alpine |
| Build | Gradle Kotlin DSL |

## Main Structure

```text
src/main/java/com/butingbe
├── ButingBeApplication.java
├── domain
│   └── user
│       ├── controller
│       ├── dto
│       ├── entity
│       ├── repository
│       └── service
└── global
    ├── common
    ├── config
    └── error
```

## How It Works

All domain controllers are exposed under the `/api/v1` prefix. The prefix is applied globally by `WebConfig`, so `UserController` only declares `/users`, while the actual external paths become `/api/v1/users/...`.

Current user API flow:

1. Client sends a request to `UserController`.
2. Request DTO validation runs through Jakarta Validation.
3. `UserServiceImpl` handles business rules.
4. `UserRepository` reads or writes `User` entities through Spring Data JPA.
5. Domain errors are converted into JSON responses by `GlobalExceptionHandler`.

Available user endpoints:

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/users/signup` | Create a user |
| GET | `/api/v1/users/signin?email={email}` | Find a user by email |

## Local Database

Docker Compose runs only PostgreSQL. The Spring Boot application is run locally through Gradle.

Start PostgreSQL:

```bash
docker compose -f docker-compose.local.yml up -d
```

Check the container:

```bash
docker compose -f docker-compose.local.yml ps
```

Local PostgreSQL connection:

| Key | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `mydb` |
| Username | `myuser` |
| Password | `mypassword` |

## Run The Application

Because `dev` currently keeps only the base `application.yaml`, pass local database properties when running the app:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/mydb \
SPRING_DATASOURCE_USERNAME=myuser \
SPRING_DATASOURCE_PASSWORD=mypassword \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
./gradlew bootRun
```

The `bootRun` task keeps running while the server is alive. Stop it with `Ctrl + C`.

## API Test Examples

Create a user:

```bash
curl -i -X POST http://localhost:8080/api/v1/users/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "nickname": "테스터",
    "provider": "google",
    "providerId": "google-123",
    "firstName": "길동",
    "lastName": "홍"
  }'
```

Look up a user:

```bash
curl -i "http://localhost:8080/api/v1/users/signin?email=test@example.com"
```

## Run Tests

```bash
./gradlew test
```

Integration tests use Testcontainers. They start their own PostgreSQL container, so the local Docker Compose database does not need to be running for tests.

## Coverage And SonarQube

The build enforces 100% line coverage for the current business API coverage target:

- `com.butingbe.domain.user.controller`
- `com.butingbe.domain.user.service`

Boilerplate and framework wiring are excluded from the coverage gate:

- Spring Boot bootstrap class
- global config/common/error classes
- DTOs
- JPA entities
- Spring Data repositories

Run the coverage gate:

```bash
./gradlew jacocoTestReport jacocoTestCoverageVerification
```

Generated reports:

```text
build/reports/jacoco/test/html/index.html
build/reports/jacoco/test/jacocoTestReport.xml
```

Run the same checks through the standard verification task:

```bash
./gradlew check
```

Run SonarQube analysis after setting your SonarQube server and token:

```bash
SONAR_HOST_URL=http://localhost:9000 \
SONAR_TOKEN=<your-token> \
./gradlew sonar
```

The SonarQube Gradle task imports JaCoCo XML coverage from `build/reports/jacoco/test/jacocoTestReport.xml`.

## Formatting

Apply Java formatting:

```bash
./gradlew spotlessApply
```

The Husky pre-commit hook also runs Spotless before committing.

## Branches

| Branch | Purpose |
| --- | --- |
| `main` | Stable base branch |
| `dev` | Integration branch for backend development |
| `feature/user` | User domain feature work |
| `hotfix/user` | Local execution and user API testing fixes |
