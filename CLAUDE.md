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
- Runtime env vars come from `.env` (see `.env.example`): `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`.

## Architecture

Package layout is **feature-first** (`user`, `auth`) with a shared `global` package. Each feature follows the same three layers:

- `presentation/` — `@RestController` + request/response DTOs (`dto/request`, `dto/response`). DTOs carry Bean Validation annotations (`@field:NotBlank` etc.) with **no custom messages** — the global handler surfaces the default message.
- `application/` — one service class **per use case** (e.g. `LogInService`, `CreateUserService`), invoked via the `operator fun invoke(...)` convention (called like `logInService(request)`).
- `domain/` — JPA entities and repository **abstractions** only. Spring Data interfaces (e.g. `UserRepository`) live here; Redis-backed stores are declared here as plain interfaces (`RefreshTokenRepository`, `VerificationCodeRepository`).
- `infrastructure/` — implementations of the domain repository interfaces (e.g. `RedisVerificationCodeRepository`, `RedisRefreshTokenRepository`): `@Repository` classes over `StringRedisTemplate` with key prefixes and TTLs. Technology details (Redis templates, TTLs) belong here, not in `domain/`.
- `exception/` — per-feature `ErrorCode` enum (implements `global.exception.ErrorCode`; `code` = enum name, Korean messages) plus one `BusinessException` subclass per error. `GlobalExceptionHandler` converts these to `ErrorResponse(code, message)`.

### Authentication flow (global/security)

Not Spring Security — a custom interceptor-based mechanism:

- `@Authenticated` on a controller class or handler method marks it as requiring auth; `AuthenticationInterceptor` validates the `Bearer` access token via `JwtTokenProvider` and stashes the user id as a request attribute.
- `@AuthUser userId: UUID` handler parameters are resolved by `AuthUserArgumentResolver` (parameter must be `UUID`).
- **Convention enforced by test**: `@AuthUser` may only appear on handlers covered by `@Authenticated` (`AuthAnnotationConventionTest`).
- Access/refresh tokens are typed via a `type` claim; refresh tokens are stored in Redis and rotated on refresh. Expirations configured under `jwt.*` in `application.yaml`.

### Persistence

- Entities extend `BaseTimeEntity` (JPA auditing `createdAt`/`updatedAt`), use time-ordered UUIDv7 ids (`UuidCreator.getTimeOrderedEpoch()`), `protected set` properties, and encode passwords in the constructor (entity takes `rawPassword` + `PasswordEncoder`).
- Schema is managed by **Flyway** (`src/main/resources/db/migration`, `V<n>__Description.sql`); Hibernate runs with `ddl-auto: validate`, so any entity change needs a migration. Tables are prefixed `tbl_`.
- Repository "find or throw" helpers are extension functions (e.g. `UserRepository.findByIdOrThrow`).

## Testing

- Test method names are Korean sentences in bac성kticks (`` fun `로그인에 성공하면 토큰을 발급하고 리프레시 토큰을 저장한다`() ``) with `// given` / `// when` / `// then` comments.
- **Unit tests** (`*ServiceTest`, `JwtTokenProviderTest`): no Spring context — MockK (`@ExtendWith(MockKExtension::class)` with `@MockK`/`@SpyK`/`@InjectMockKs`) plus kotest assertions (`shouldBe`, `shouldThrow`).
- **Integration tests** (`*IntegrationTest`): extend `support/IntegrationTest`, which boots the full app (`@SpringBootTest` + MockMvc) against Testcontainers and replaces the SMTP sender with `FakeVerificationCodeSender` (`@Primary`; read sent codes via `lastCodeFor(email)`). The base class provides `mockMvc`, `loggedInUser()` (persists a user and issues real tokens), `Any.toJson()`, and an `authorization(accessToken)` DSL helper, and after each test truncates all tables and flushes Redis — individual tests never clean up. Use the MockMvc Kotlin DSL (`mockMvc.post("/users") { ... }.andExpect { ... }`).
- Entity test data comes from fixture objects with overridable defaults (`UserFixture.user()`).

## Conventions (from CONTRIBUTING.md)

- Git flow without release branches: PRs target `develop`; branch names are `<feat|fix|refactor|hotfix>/<kebab-description>-<issue#>` (e.g. `feat/chat-session-12`).
- Commit messages: `<type>: <subject>` with types `feat|fix|refactor|docs|style|test|chore`, imperative subject, optional scope (`feat(auth): ...`). Commit subjects and code messages are written in Korean.
- Code style follows the official Kotlin Coding Conventions.
