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
- Runtime env vars come from `.env` (see `.env.example`): `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Optional: `GITHUB_WEB_CLIENT_ID/SECRET`, `GOOGLE_WEB_CLIENT_ID/SECRET` (web OAuth pair — without them only web login returns 503) and `AUTH_COOKIE_SECURE=false` for local http.

## Architecture

Package layout is **feature-first** (`user`, `auth`) with a shared `global` package. Each feature follows the same three layers:

- `presentation/` — `@RestController` + request/response DTOs (`dto/request`, `dto/response`). DTOs carry Bean Validation annotations (`@field:NotBlank` etc.) with **no custom messages** — the global handler surfaces the default message.
- `application/` — one service class **per use case** (e.g. `LogInService`, `CreateUserService`). Every service exposes exactly one public entry point, and it **must** be `operator fun invoke(...)` (called like `logInService(request)`) — never a named method like `createUser()`. Helper functions stay `private`.
  - A service whose input is the request body alone takes the request DTO as is. When the body must travel with a path variable, query parameter, or `@AuthUser` id, they are merged into one `application/port/in/<UseCase>Command` data class (no validation annotations), built by the request DTO's `toCommand(...)` — e.g. `inviteTeamMemberService(request.toCommand(teamId, userId))`. Services without a body keep plain parameters.
- `domain/` — JPA entities and repository **abstractions** only. Spring Data interfaces (e.g. `UserRepository`) live here; Redis-backed stores are declared here as plain interfaces (`RefreshTokenRepository`, `VerificationCodeRepository`).
- `infrastructure/` — implementations of the domain repository interfaces (e.g. `RedisVerificationCodeRepository`, `RedisRefreshTokenRepository`): `@Repository` classes over `StringRedisTemplate` with key prefixes and TTLs. Technology details (Redis templates, TTLs) belong here, not in `domain/`.
- `exception/` — per-feature `ErrorCode` enum (implements `global.exception.ErrorCode`; `code` = enum name, Korean messages) plus one `BusinessException` subclass per error. `GlobalExceptionHandler` converts these to `ErrorResponse(code, message)`.

### Authentication flow (global/security)

Not Spring Security — a custom interceptor-based mechanism:

- `@Authenticated` on a controller class or handler method marks it as requiring auth; `AuthenticationInterceptor` resolves the access token — `Authorization: Bearer` header first, else the `kukie_access` cookie — validates it via `JwtTokenProvider` and stashes the user id as a request attribute. Header wins when both are present (a blank header counts as absent; a present-but-malformed header is a 401, never a cookie fallback). Cookie auth is accepted only when the browser's `Sec-Fetch-Site` is `same-origin`/`same-site`/`none` — `cross-site` or missing → 403 `CROSS_SITE_COOKIE` (fail-closed; `SameSite=Lax` alone lets top-level GETs through). Desktop app and agent send Bearer; the web client relies on cookies.
- Login/refresh responses return tokens **both** as JSON and as httpOnly cookies (`kukie_access`, `kukie_refresh`; `Secure`, `SameSite=Lax`, `Path=/`, max-age = token expiry) via `AuthCookies` — no per-client branching. `/auth/refresh` reads the refresh token from the body, else from the cookie; `/auth/logout` clears both cookies. Cookie names/`secure` live in `AuthCookieProperties` (`auth.cookie.*`).
- `@AuthUser userId: UUID` handler parameters are resolved by `AuthUserArgumentResolver` (parameter must be `UUID`).
- **Convention enforced by test**: `@AuthUser` may only appear on handlers covered by `@Authenticated` (`AuthAnnotationConventionTest`).
- Access/refresh tokens are typed via a `type` claim; refresh tokens are stored in Redis and rotated on refresh. Expirations configured under `jwt.*` in `application.yaml`.

### OAuth (auth feature)

- **Two clients call this API: the desktop app and the web app.** Both use the authorization code flow with PKCE and POST `code` + `redirectUri` (+ `codeVerifier`) to this server, which exchanges the code using a client secret. The desktop app catches the redirect on `http://127.0.0.1:<any-port>`; the web app is redirected to `<origin>/auth/callback`.
- **Each provider has two credential pairs** (`OAuthProperties.Registration`: desktop `clientId/clientSecret`, web `webClientId/webClientSecret`). `OAuthCredentialsResolver` picks the pair from the request's `redirectUri`: `http://127.0.0.1:...` → desktop, anything else (including `http://localhost:5173/...` for local web dev) → web. A code issued to one client cannot be exchanged with the other's secret, so pairs never mix. Missing web pair → `OAUTH_CLIENT_NOT_CONFIGURED` (503) for web logins only.
- **Google**: the desktop pair is a **"Desktop app" type** client (no redirect-URI allowlist; Google auto-allows loopback). The web pair must be a **"Web application" type** client with the callback URIs registered. Keep both; do not convert one into the other.
- **GitHub**: OAuth Apps have a single callback URL, so the desktop pair's app uses a loopback callback (port ignored) and the web pair is a **second OAuth App** whose callback is the web address.

### Persistence

- Entities extend `BaseTimeEntity` (JPA auditing `createdAt`/`updatedAt`), use time-ordered UUIDv7 ids (`UuidCreator.getTimeOrderedEpoch()`), `protected set` properties, and encode passwords in the constructor (entity takes `rawPassword` + `PasswordEncoder`).
- Schema is managed by **Flyway** (`src/main/resources/db/migration`, `V<n>__Description.sql`); Hibernate runs with `ddl-auto: validate`, so any entity change needs a migration. Tables are prefixed `tbl_`.
- Repository "find or throw" helpers are extension functions (e.g. `UserRepository.findByIdOrThrow`).

## Testing

- Test method names are Korean sentences in backticks (`` fun `로그인에 성공하면 토큰을 발급하고 리프레시 토큰을 저장한다`() ``) with `// given` / `// when` / `// then` comments.
- **Test ordering**: within each use case (API/service being tested), the success case comes first, followed by its exception cases.
- **Unit tests** (`*ServiceTest`, `JwtTokenProviderTest`): no Spring context — MockK (`@ExtendWith(MockKExtension::class)` with `@MockK`/`@SpyK`/`@InjectMockKs`) plus kotest assertions (`shouldBe`, `shouldThrow`).
- **Integration tests** (`*IntegrationTest`): extend `support/IntegrationTest`, which boots the full app (`@SpringBootTest` + MockMvc) against Testcontainers and replaces the SMTP sender with `FakeVerificationCodeSender` (`@Primary`; read sent codes via `lastCodeFor(email)`). The base class provides `mockMvc`, `loggedInUser()` (persists a user and issues real tokens), `Any.toJson()`, and an `authorization(accessToken)` DSL helper, and after each test truncates all tables and flushes Redis — individual tests never clean up. Use the MockMvc Kotlin DSL (`mockMvc.post("/users") { ... }.andExpect { ... }`).
- Entity test data comes from fixture objects with overridable defaults (`UserFixture.user()`).

## Conventions (from CONTRIBUTING.md)

- Git flow without release branches: PRs target `develop`; branch names are `<feat|fix|refactor|hotfix>/<kebab-description>-<issue#>` (e.g. `feat/chat-session-12`).
- Commit messages: `<type>: <subject>` with types `feat|fix|refactor|docs|style|test|chore`, imperative subject, optional scope (`feat(auth): ...`). Commit subjects and code messages are written in Korean.
- Code style follows the official Kotlin Coding Conventions.
