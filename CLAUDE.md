# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kukie server — Kotlin 2.3 / Spring Boot 4.1 / Java 25 REST API backed by PostgreSQL (JPA + Flyway) and Redis, with JWT-based authentication.

## Commands

```bash
./gradlew build                # compile + test
./gradlew test                 # run all tests
./gradlew test --tests "com.duro.kukie.global.security.AuthAnnotationConventionTest"   # single test class
./gradlew bootRun              # run the app (spring-boot-docker-compose starts postgres/redis from docker-compose.yaml automatically)
```

- Integration tests use Testcontainers (PostgreSQL + Redis, wired via `TestcontainersConfig`) and the `test` profile (`src/test/resources/application-test.yaml`), so Docker must be running.
- Runtime env vars come from `.env` (see `.env.example` and `application.yaml`). Required properties (`@NotBlank` on OAuth credentials, `WEB_APP_URL`, `KUKIE_INTERNAL_TOKEN`, and a 32-byte base64 `KUKIE_CREDENTIAL_KEY`) are validated at startup; missing or blank values fail startup.

## Architecture

This server is the **Kukie API server**: the only service that talks to the database. `kukie-agent` runs workflows (LLM loop, guardrails, `kubectl`) and stores/reads everything through this server's `/internal/**` API.

### Domains

Package layout is **feature-first** with a shared `global` package:

- `user` — User sign-up, email verification codes, and profile management (`User`).
- `auth` — Password and OAuth (GitHub/Google with PKCE) login, JWT access/refresh token rotation, logout, and one-time handoff codes.
- `team` — Team lifecycle, member roles (`ADMIN`/`MEMBER`), and email invitations (`Team`, `TeamMembership`, `TeamInvitation`).
- `inbox` / `notification` — Aggregated inbox of pending team invitations and user notifications (`Notification`).
- `cluster` — Team-owned Kubernetes clusters (uploaded kubeconfigs and customer AWS EKS via STS AssumeRole/CloudFormation) and AES-256-GCM encrypted credentials (`Cluster`, `AwsConnection`).
- `chat` — Cluster-bound conversation sessions (`ChatSession`, exposed at `/conversations`) and agent execution runs (`ChatRun`).
- `actionplan` — Guardrail state machine (`ActionPlan`) for mutating agent tool calls: dry-run, approval/rejection, and execution results.
- `gitops` — GitHub App installs bound to teams (`GitHubInstallation`, one team per installation), cluster ↔ manifest repo links (`ManifestRepo`, normalized lowercase `owner/repo`), and repo-scoped installation tokens for the agent. The App private key is used only in `GitHubAppCredentials` (App JWT signing + token cache).

### Layers

Each feature follows the same layers:

- `presentation/` — `@RestController` + request/response DTOs (`dto/request`, `dto/response`). DTOs carry Bean Validation annotations (`@field:NotBlank` etc.); the global handler returns the first field error's message. There is no Jackson Kotlin module: request bodies bind through the primary constructor by parameter name, so **Kotlin default values are ignored** — a missing non-null field (including a primitive `Boolean`) is a 400, and every optional field must be nullable (`shared: Boolean? = null`, defaulted in `toCommand`).
- `application/` — one service class **per use case** (e.g. `LogInService`, `CreateUserService`). Every use-case service exposes exactly one public entry point, and it **must** be `operator fun invoke(...)` (called like `logInService(request)`) — never a named method like `createUser()`. Helper functions stay `private`. Shared permission components (`TeamPermission`, `ClusterPermission`, `ChatPermission`) are the only exception.
  - A service whose input is the request body alone takes the request DTO as is — unless a field must become a domain type (e.g. an email string → `Email`), in which case it takes a `<UseCase>Command` built by `toCommand()`. When the body must travel with a path variable, query parameter, or `@AuthUser` id, they are merged into one `application/port/in/<UseCase>Command` data class (no validation annotations), built by the request DTO's `toCommand(...)`. Services without a body keep plain parameters.
- `domain/` — JPA entities and repository **abstractions** only. Spring Data interfaces (e.g. `UserRepository`) live here; Redis-backed stores are declared here as plain interfaces (`RefreshTokenRepository`, `VerificationCodeRepository`, `HandoffCodeRepository`).
- `infrastructure/` — implementations of domain repository interfaces and outbound ports (`@Repository` over `StringRedisTemplate`, AWS SDK gateways, mail senders, parsers). Technology details belong here, not in `domain/`.
- `exception/` — per-feature `ErrorCode` enum (implements `global.exception.ErrorCode`; `code` = enum name, Korean messages) plus one `BusinessException` subclass per error. `GlobalExceptionHandler` converts these to `ErrorResponse(code, message)`.

### Security & Interceptors (`global/security`, `team/presentation`)

Not Spring Security — a custom interceptor chain (`InternalApiInterceptor` → `AuthenticationInterceptor` → `TeamRoleInterceptor`):

- **Conventions enforced by tests**:
  - `@AuthUser userId: UUID` may only appear on handlers covered by `@Authenticated` (`AuthAnnotationConventionTest`).
  - Every handler under `/internal/**` must have `@InternalApi`, and no other handler may (`InternalApiConventionTest`). The reverse proxy must not expose `/internal` publicly.
  - Every handler taking a `{teamId}` path variable must declare `@TeamRoleRequired(TeamRole.MEMBER|ADMIN)`, which is only valid on `@Authenticated` handlers with a `UUID` `{teamId}` parameter (`TeamRoleRequiredConventionTest`).
- **Token resolution & CSRF**: `AuthenticationInterceptor` checks `Authorization: Bearer` first, else the `__Host-kukie_access` cookie (`accessCookieName` in `AuthCookieProperties`, which adds `__Host-` when `secure = true` and never falls back to the unprefixed cookie). A present-but-malformed Bearer header is an immediate 401 (no cookie fallback). Cookie auth is accepted only when `Sec-Fetch-Site` is `same-origin` or `none` — otherwise 403 `CROSS_SITE_COOKIE`. Login/refresh return tokens **both** as JSON and as httpOnly cookies via `AuthCookies`.
- **Internal API — Entry vs non-entry**: The user's Bearer token is taken **only at the entry** of a workflow, where the server checks user permissions (`@InternalApi` + `@Authenticated`: conversation `context`, `runs/by-request/{requestId}`, `POST conversations/{id}/runs`, cluster `connection`). **Every other internal handler takes `X-Kukie-Internal-Token` only** (run update/interrupt, plan create/list/get and state actions, cluster status, manifest repos and repo tokens, conversation `repo-link-state`) because an access token expires in 30 minutes and a long-running agent run must still record its results. Never add `@Authenticated`/`@AuthUser` to a non-entry internal handler.

### Persistence

- Entities extend `BaseTimeEntity` (JPA auditing `createdAt`/`updatedAt`), use time-ordered UUIDv7 ids (`UuidCreator.getTimeOrderedEpoch()`), `protected set` properties, and encode passwords in the constructor.
- Schema is managed by **Flyway** (`src/main/resources/db/migration`, `V<n>__Description.sql`); Hibernate runs with `ddl-auto: validate`, so any entity change needs a migration. Tables are prefixed `tbl_`.
- `spring.jpa.open-in-view` is **false**: a DB connection is borrowed only for a transaction or a single repository call. Never hold a transaction across external I/O (DNS lookups, AWS calls, mail sending), and do not rely on lazy loading or dirty checking outside a service transaction.
- Repository "find or throw" helpers are extension functions (e.g. `UserRepository.findByIdOrThrow`).
- **jsonb**: opaque agent JSON (`responsePayload`, `agentMessages`, `usageSummary`, plan `payload`/`decision`/`executionResult`) is mapped with `@JdbcTypeCode(SqlTypes.JSON)` on plain `Map<String, Any?>`/`List<Any?>` so snake_case keys are preserved as stored. Because PostgreSQL `jsonb` rejects NUL (`\u0000`), entity methods sanitize agent JSON through `global.util.withoutNul()` (NUL → U+FFFD). Always replace a JSON column value as a whole rather than mutating it in place.
- Time columns (`startedAt`, `finishedAt`, `appliedAt`) are written in UTC (`global.util.utcNow()`).
- Email addresses are `global.domain.Email` (`@JvmInline value class`, trims + lowercases) in entities, commands, and repositories, while DTOs and mail ports use `String` (converted in `toCommand()`). Keep JPA annotations on fields (`@field:...`) to avoid Kotlin value-class mangled getter issues.

## Testing

- Test method names are Korean sentences in backticks (`` fun `로그인에 성공하면 토큰을 발급하고 리프레시 토큰을 저장한다`() ``) with `// given` / `// when` / `// then` comments.
- **Test ordering**: within each use case, the success case comes first, followed by its exception cases.
- **Unit tests** (`*ServiceTest`, `JwtTokenProviderTest`): no Spring context — MockK (`@ExtendWith(MockKExtension::class)`) plus kotest assertions (`shouldBe`, `shouldThrow`).
- **Integration tests** (`*IntegrationTest`): extend `support/IntegrationTest` (`@SpringBootTest` + MockMvc against Testcontainers, with fake mail senders, `FakeOAuthClient`, `FakeHostResolver`, `FakeEksAccessGateway`, `FakeGitHubAppGateway`, and `FakeStackNoticeResponder`). The base class truncates all tables and flushes Redis after each test — individual tests never clean up. Use the MockMvc Kotlin DSL.
- Entity test data comes from fixture objects with overridable defaults (`UserFixture`, `TeamFixture`, `ClusterFixture`, `ChatFixture`, `ActionPlanFixture`, `GitOpsFixture`).
- **Concurrency tests** (`*ConcurrencyTest`): open overlapping transactions with `TransactionTemplate` + latches or thread pools, and poll `pg_stat_activity` (`wait_event_type = 'Lock'`) from an outside connection when waiting for a row lock.

## Conventions

- Follow [CONTRIBUTING.md](./CONTRIBUTING.md) for branch strategy, branch naming, commit messages, and Kotlin code style.
- Comments: write one only for a constraint the code cannot show and a reader would likely break without it (e.g. a lock order), as a single short line. Do not restate the code, cite planning docs or issue numbers, or narrate design history. Test `// given` / `// when` / `// then` markers are the exception.
