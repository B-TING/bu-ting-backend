# B-ting Backend

B-ting backend is a Spring Boot REST API server for a travel planning and record-sharing service. It covers social
login, travel plans with AI-generated itineraries, place search backed by external tourism APIs, team travel with
invites, expense settlement, travel records with reviews and comments, regional chat, and file uploads to S3.

## Tech Stack

| Area          | Stack                                                                  |
|---------------|------------------------------------------------------------------------|
| Language      | Java 25                                                                |
| Framework     | Spring Boot 4.0.6 (Web MVC, Validation, Actuator, WebSocket)            |
| Build         | Gradle Wrapper, Kotlin DSL                                             |
| Security      | Spring Security, OAuth2 Client (Google / Naver / Kakao), opaque tokens  |
| Persistence   | Spring Data JPA, Hibernate, Flyway                                     |
| Database      | PostgreSQL                                                             |
| AI            | Spring AI 2.0.0, OpenAI-compatible chat model                          |
| File Storage  | AWS SDK v2 S3 (presigned URLs)                                         |
| Realtime      | STOMP over WebSocket                                                   |
| i18n          | `messages.properties` — ko, en, ja, zh                                 |
| Test          | JUnit 5, Mockito, Spring Boot Test, MockMvc, Spring REST Docs, Testcontainers |
| Coverage      | JaCoCo 0.8.15                                                          |
| Formatting    | Spotless, Google Java Format                                           |
| Local Infra   | Docker Compose, PostgreSQL 16 Alpine                                   |
| CI / Deploy   | GitHub Actions, Docker Hub, EC2                                        |

## Main Structure

```text
src/main/java/com/butingbe
├── ButingBeApplication.java
├── domain
│   ├── auth            # OAuth login, token issuance, AuthenticatedUser, security filter
│   ├── chat            # Regional chatrooms over STOMP/WebSocket
│   ├── file            # S3 uploads and file metadata
│   ├── place           # Place catalog backed by TourAPI and Google Places
│   ├── reward          # Reward catalog, grants, point ledger and badges (Phase 1)
│   ├── route           # Travel time, distance, visit-order and alternative routes
│   ├── station         # Station reference data
│   ├── storage         # Luggage storage locations
│   ├── travel          # Travels, plans, and AI itinerary generation (ai package)
│   ├── travelexpense   # Expenses and settlements
│   ├── travelrecord    # Travel records, reviews, likes, bookmarks, comments
│   ├── travelsurvey    # Travel preference survey
│   ├── notification    # Push: device tokens, subscriptions, settings (Phase 2)
│   ├── travelteam      # Team members and invitations
│   ├── user            # User profile
│   ├── zonetitle       # Zone titles and city grade (Phase 2)
│   └── zoneevent       # Zone events: on-site GPS missions, rounds and slots (Phase 1-2)
└── global
    ├── common          # ApiResponse, BaseEntity, TimestampEntity
    ├── config          # AppConfig, SecurityConfig, WebConfig, WebSocketConfig, S3Config, I18nConfig
    └── error           # GlobalExceptionHandler and domain exceptions

src/main/resources
├── db/migration        # Flyway SQL migrations
├── static/docs         # index.html and openapi3.yaml generated from REST Docs
├── templates
└── messages*.properties

src/test/java/com/butingbe
└── support             # AbstractContainerTest — shared Testcontainers base class
```

Each domain separates responsibilities into `controller`, `service`, `repository`, `entity`, and
`dto/request` / `dto/response`. Only functionality genuinely shared across domains belongs in `global`.

## How It Works

All domain controllers are exposed under the `/api/v1` prefix. `WebConfig` applies the prefix globally to every
`@RestController` under `com.butingbe.domain`, so `UserController` declares only `/users` while the external path
becomes `/api/v1/users/...`. Do not repeat `/api/v1` in a controller's `@RequestMapping`.

Request flow:

1. The client calls a controller under `/api/v1`.
2. Request DTOs are validated with Jakarta Validation and `@Valid`.
3. Authenticated endpoints resolve the caller through `@AuthenticationPrincipal AuthenticatedUser`, populated by
   `OpaqueTokenAuthenticationFilter`.
4. Services hold business rules; repositories read and write entities through Spring Data JPA.
5. Successes and failures are wrapped in `ApiResponse<T>`. Domain exceptions are converted to HTTP status codes by
   `GlobalExceptionHandler`.

### API Overview

| Base path                                    | Controller                      | Description                                        |
|----------------------------------------------|---------------------------------|----------------------------------------------------|
| `/api/v1/auth`                               | `AuthController`                | OAuth login                                        |
| `/api/v1/users`                              | `UserController`                | Sign-up, profile read/update/delete                |
| `/api/v1/travel-surveys`                      | `TravelSurveyController`        | Travel preference survey                           |
| `/api/v1/places`                             | `PlaceController`               | Place search, nearby, festivals, detail            |
| `/api/v1/places/reviews`                     | `PublicPlaceReviewController`   | Public place reviews                               |
| `/api/v1/places/travel-records`              | `PublicPlaceTravelRecordController` | Travel records for a place                     |
| `/api/v1/storage-locations`                  | `StorageLocationController`     | Luggage storage lookup                             |
| `/api/v1/travels`                            | `TravelController`              | Travel plans, AI plan generation, status           |
| `/api/v1/plans`                              | `PlanController`                | Plan places: add, reorder, edit, visit, delete     |
| `/api/v1/plans/{planId}/route`               | `TravelRouteController`         | Travel legs and totals, and visit-order optimisation |
| `/api/v1/travels/{travelId}/route`           | `TravelRouteOptimizeController` | Visit-order optimisation across every day of a travel |
| `/api/v1/plans/{planId}/reboot`              | `TravelRebootController`        | Rebuild the day's remaining plan from the current location and time left |
| `/api/v1/travels/{travelId}/expenses`        | `TravelExpenseController`       | Expense CRUD and summary                           |
| `/api/v1/travels/{travelId}/expenses/settlements` | `TravelSettlementController` | Settlement confirmation                          |
| `/api/v1/travels/{travelId}/records`         | `TravelRecordController`        | Travel record read, edit, publish                  |
| `/api/v1/travel-records`                     | `PublicTravelRecordController`  | Feed, bookmarks, likes, comments, clone-to-travel  |
| `/api/v1/travel/team`                        | `TravelTeamController`          | Team members, leader, invitations                  |
| `/api/v1/chat/rooms`                         | `LocalChatroomController`       | Chatroom lookup, join, exit, message history       |
| `/api/v1/files`                              | `FileController`                | Multipart upload to S3                             |
| `/api/v1/zone-events`                        | `ZoneEventController`           | Active zone events and event detail (Phase 1)      |
| `/api/v1/zone-events/{eventId}/participations` | `ZoneEventParticipationController` | Join, submit, cancel, and my participations for an event |
| `/api/v1/zone-events/*/album`, `/zones/*/album`, `/zone-event-rounds/*/album` | `ZoneEventAlbumController`       | Public album feeds and participation visibility |
| `/api/v1/zone-event-participations/{id}/likes`, `/comments`, `/reports` | `ZoneEventSocialController` | Likes, comments, and reports on public participations |
| `/api/v1/admin/zone-event-participations`      | `AdminReviewController`         | Operator review queue: approve, reject, revoke, unhide |
| `/api/v1/users/me/device-tokens`, `/zone-subscriptions`, `/notification-settings` | `UserNotificationController` | Push tokens, zone subscriptions, notification settings |
| `/api/v1/admin/push`                          | `AdminPushController`           | Operator immediate push to a zone or everyone |
| `/api/v1/zone-titles`, `/users/me/zone-titles`   | `ZoneTitleController`           | Zone title definitions, ownership, equip (Phase 2) |
| `/api/v1/users/me/zone-event-participations` | `ZoneEventMeController`         | My zone event participation history (cursor paging) |
| `/api/v1/users/me/rewards`, `/point-ledger`  | `UserRewardController`          | My reward summary (badges by zone, balance) and point ledger |
| `/api/v1/admin/zone-events`                  | `AdminZoneEventController`      | Operator event CRUD and state transitions (ADMIN/MANAGER) |
| `/api/v1/admin/reward-catalog`               | `AdminRewardCatalogController`  | Operator reward catalog CRUD and grant history (ADMIN/MANAGER) |
| `/api/v1/admin/zone-event-rounds`            | `AdminRoundController`          | Operator round console: calendar, slots, backup/rain-swap targets, open/close/settle, settlement report (ADMIN/MANAGER) |
| `/api/v1/zone-event-rounds/current`          | `ZoneEventRoundController`      | Current round status per zone (OPEN/REST/UPCOMING) |

The generated OpenAPI specification lives at `src/main/resources/static/docs/openapi3.yaml`.

### WebSocket

| Item                    | Value                |
|-------------------------|----------------------|
| STOMP endpoint          | `/ws-stomp`          |
| Application prefix      | `/pub`               |
| Broker prefix           | `/sub`               |
| Message mapping         | `/pub/chat/message`  |

## Local Database

Docker Compose runs only PostgreSQL. The Spring Boot application runs locally through Gradle.

Start PostgreSQL:

```bash
docker compose -f docker-compose.local.yml up -d
```

Check the container:

```bash
docker compose -f docker-compose.local.yml ps
```

Local PostgreSQL connection values:

| Key      | Value        |
|----------|--------------|
| Host     | `localhost`  |
| Port     | `5433`       |
| Database | `mydb`       |
| Username | `myuser`     |
| Password | `mypassword` |

Local container data is stored in `postgres_data/`. It is git-ignored — do not read, edit, or commit it.

## Database Migrations

Flyway owns the schema. Hibernate runs with `ddl-auto: validate`, so entity changes without a matching migration fail
at startup.

- Migrations live in `src/main/resources/db/migration` as `V<n>__<description>.sql`.
- Never edit a migration that has already been applied or shared. Add the next version number instead.
- Change the entity and the migration in the same unit of work.

## Run The Application

Create a `.env` file in the project root. The Gradle `bootRun` task loads it and injects the values as environment
variables before starting Spring Boot. `.env` is git-ignored — never commit it or place real keys anywhere tracked.

Minimum values for a local run:

```dotenv
DB_URL=jdbc:postgresql://localhost:5433/mydb
DB_USERNAME=myuser
DB_PASSWORD=mypassword
```

Environment variables referenced by `application.yaml`, by feature:

| Feature       | Variables                                                                                        |
|---------------|--------------------------------------------------------------------------------------------------|
| Database      | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`                                                            |
| AI            | `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL`                                                           |
| Google OAuth  | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `GOOGLE_ALLOWED_AUDIENCES`, `GOOGLE_AND_DEBUG_CLIENT_ID`, `GOOGLE_AND_RELEASE_CLIENT_ID` |
| Naver OAuth   | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `NAVER_REDIRECT_URI`                                    |
| Kakao OAuth   | `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `KAKAO_ALLOWED_AUDIENCES`, `KAKAO_AND_DEBUG_CLIENT_ID` |
| Place APIs    | `TOUR_API_BASE_URL`, `TOURISM_API_KEY`, `GOOGLE_PLACES_BASE_URL`, `GOOGLE_PLACES_API_KEY`         |
| S3            | `S3_BUCKET`, `AWS_REGION`, `S3_KEY_PREFIX`, `S3_MAX_FILE_SIZE`, `S3_PRESIGNED_URL_EXPIRATION`     |
| Upload limits | `FILE_MAX_SIZE`, `FILE_MAX_REQUEST_SIZE`                                                          |
| Invitations   | `TRAVEL_INVITE_BASE_URL`                                                                          |
| Routing       | `ROUTE_GOOGLE_ENABLED` (off by default), `ROUTE_GOOGLE_API_KEY` (falls back to `GOOGLE_PLACES_API_KEY`) |

Run the application:

```bash
./gradlew bootRun
```

`bootRun` also imports `application-oauth.yaml` from the classpath when present (optional, git-ignored). The task keeps
running while the server is alive; stop it with `Ctrl + C`. When running the packaged JAR, supply the same variables as
process environment variables, because `.env` loading is wired only into `bootRun`.

## Run Tests

```bash
./gradlew test
```

Run a single test class:

```bash
./gradlew test --tests 'com.butingbe.domain.user.service.UserServiceImplTest'
```

Integration tests use Testcontainers and start their own PostgreSQL container, so the local Docker Compose database
does not need to be running. Docker itself is required — if a test fails because Docker is unavailable, that is an
environment failure, not a code failure.

## Coverage

The build enforces **80% bundle line coverage** through `jacocoTestCoverageVerification`, which `check` depends on.

Excluded from the coverage gate:

- `**/ButingBeApplication*` — Spring Boot bootstrap class
- `**/global/config/**` — framework wiring
- `**/*Dto*` — data transfer objects

Do not widen these exclusions to get past the gate.

Run the coverage gate:

```bash
./gradlew jacocoTestReport jacocoTestCoverageVerification
```

Generated reports:

```text
build/reports/jacoco/test/html/index.html
build/reports/jacoco/test/jacocoTestReport.xml
```

## Verification

Run both before pushing:

```bash
./gradlew spotlessApply
./gradlew check
```

`check` runs the tests, the JaCoCo report, and the coverage gate.

## Formatting

Java formatting is Spotless with Google Java Format. Do not hand-align code — run:

```bash
./gradlew spotlessApply
```

The Husky **pre-push** hook runs `./gradlew spotlessApply check` automatically and blocks the push if Spotless modified
any file, so review and commit those changes before pushing again. The `pre-commit` hook intentionally does nothing,
which keeps commits easy to split.

Install the hooks once after cloning:

```bash
npm install
```

## Contributing

`dev` is the integration branch and `main` is the production branch.

1. Open or pick a GitHub issue. Large features use the `Parent Feature` template; the actual work is split into
   `Task / Sub-issue` items that reference `Parent: #<number>`.
2. Update remote `dev` and branch from it as `<type>/<short-description>` — for example `feature/storage-location` or
   `fix/oauth-token-validation`. Allowed types: `feature`, `fix`, `refactor`, `test`, `docs`, `chore`, `hotfix`.
3. Keep one issue per branch. Do not mix unrelated refactors, formatting, or dependency bumps.
4. Write Conventional Commits — `<type>(<scope>): <summary>`, for example `feat(storage): add luggage locker search API`.
   Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`. Reference issues with `Refs #<number>`.
5. Pass `./gradlew spotlessApply` and `./gradlew check` before pushing.
6. Open a pull request against `dev` — never from a feature branch straight to `main`. Fill in
   `.github/pull_request_template.md` and include `Closes #<number>` plus any API, DB, or configuration impact.
7. Merge only after CI passes and review is complete. Do not bypass a failing CI or lower the verification bar.
8. After merging, delete the branch and confirm the issue closed.

## CI

Pull requests targeting `dev` or `main` run `.github/workflows/ci.yml`, which sets up Temurin Java 25 and runs
`./gradlew check --no-daemon`. The `check` status check is required on the `dev` branch (no review approval required).

### Auto-merge

Add the `automerge` label to a PR targeting `dev` and `.github/workflows/automerge.yml` enables GitHub native
auto-merge (squash); GitHub merges it once the required `check` status check passes. Only `dev`-targeted,
non-draft PRs are eligible — `main` release PRs are merged manually so the deploy workflow triggers.

## Production Deployment

A push to `main` runs `.github/workflows/deploy.yml`, which verifies the project, builds and pushes images tagged with
the immutable commit SHA plus `latest` to Docker Hub, and replaces the application container on EC2. EC2 does not clone
this repository. Do not push to `main` directly — release by merging a reviewed release PR from `dev`.

Configure these GitHub repository secrets:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
EC2_HOST
EC2_USER
EC2_SSH_PRIVATE_KEY
EC2_SSH_KNOWN_HOSTS
```

The Amazon Linux instance uses `ec2-user`, so set `EC2_USER=ec2-user` and `EC2_HOST` to the Elastic IP. Before the first
deployment, install Docker with the Compose plugin and keep the existing `/home/ec2-user/app/docker-compose.yml` and
`/home/ec2-user/app/.env` files on EC2. The workflow updates only the Docker Hub namespace and `IMAGE_TAG` in that
`.env`, pulls the image tagged with the `main` commit SHA, recreates only the `app` container, and waits for port 8080
to accept connections before pruning old images.

For an RDS connection using `sslmode=verify-full`, set the JDBC URL's certificate parameter to
`sslrootcert=/app/certs/global-bundle.pem`. The AWS RDS global CA bundle ships at that path in the runtime image.

## Branches

| Branch      | Purpose                                    |
|-------------|--------------------------------------------|
| `main`      | Production branch; pushes trigger deploy    |
| `dev`       | Integration branch for backend development  |
| `<type>/*`  | Short-lived work branches created from `dev` |
| `hotfix/*`  | Urgent production fixes created from `main` |
