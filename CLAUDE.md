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
- Runtime env vars come from `.env` (see `.env.example`): `JWT_SECRET`, `GITHUB_CLIENT_ID/SECRET`, `GOOGLE_CLIENT_ID/SECRET`, `WEB_APP_URL` (link target of mail buttons) — every OAuth value and `WEB_APP_URL` are `@NotBlank`, so the app fails to start if any is missing or empty. Mail goes out through AWS SES (`aws.region`, `aws.ses.*`); credentials come from the SDK default chain (`AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY` or the `~/.aws` default profile locally, an IAM role when deployed). Also required: `KUKIE_INTERNAL_TOKEN` (`internal.token`, shared with the agent; `@NotBlank`) and `KUKIE_CREDENTIAL_KEY` (`cluster.credential-key`, base64 of exactly 32 bytes — `openssl rand -base64 32`; `AesGcmCredentialCipher` refuses to start otherwise). Optional: `AUTH_COOKIE_SECURE=false` for local http, `AWS_REGION` (default `ap-northeast-2`), `KUKIE_ALLOW_LOCAL_CLUSTER=true` to allow private/loopback cluster addresses (local kind only; default false).

## Architecture

This server is the **Kukie API server**: the only service that talks to the database. kukie-agent runs workflows (LLM loop, guardrails, kubectl) and stores/reads everything through this server's `/internal/**` API.

Package layout is **feature-first** (`user`, `auth`, `team`, `inbox`, `notification`, `cluster`) with a shared `global` package. Each feature follows the same three layers:

- `presentation/` — `@RestController` + request/response DTOs (`dto/request`, `dto/response`). DTOs carry Bean Validation annotations (`@field:NotBlank` etc.) with **no custom messages** — the global handler surfaces the default message. There is no Jackson Kotlin module: request bodies bind through the primary constructor by parameter name, so **Kotlin default values are ignored** — a missing non-null field (including a primitive `Boolean`) is a 400, and every optional field must be nullable (`shared: Boolean? = null`, defaulted in `toCommand`).
- `application/` — one service class **per use case** (e.g. `LogInService`, `CreateUserService`). Every service exposes exactly one public entry point, and it **must** be `operator fun invoke(...)` (called like `logInService(request)`) — never a named method like `createUser()`. Helper functions stay `private`.
  - A service whose input is the request body alone takes the request DTO as is — unless a field must become a domain type (e.g. an email string → `Email`), in which case it also takes a `<UseCase>Command` built by `toCommand()` (e.g. `logInService(request.toCommand())`). When the body must travel with a path variable, query parameter, or `@AuthUser` id, they are merged into one `application/port/in/<UseCase>Command` data class (no validation annotations), built by the request DTO's `toCommand(...)` — e.g. `inviteTeamMemberService(request.toCommand(teamId, userId))`. Services without a body keep plain parameters.
- `domain/` — JPA entities and repository **abstractions** only. Spring Data interfaces (e.g. `UserRepository`) live here; Redis-backed stores are declared here as plain interfaces (`RefreshTokenRepository`, `VerificationCodeRepository`).
- `infrastructure/` — implementations of the domain repository interfaces (e.g. `RedisVerificationCodeRepository`, `RedisRefreshTokenRepository`): `@Repository` classes over `StringRedisTemplate` with key prefixes and TTLs. Technology details (Redis templates, TTLs) belong here, not in `domain/`.
- `exception/` — per-feature `ErrorCode` enum (implements `global.exception.ErrorCode`; `code` = enum name, Korean messages) plus one `BusinessException` subclass per error. `GlobalExceptionHandler` converts these to `ErrorResponse(code, message)`.

### Authentication flow (global/security)

Not Spring Security — a custom interceptor-based mechanism:

- `@Authenticated` on a controller class or handler method marks it as requiring auth; `AuthenticationInterceptor` resolves the access token — `Authorization: Bearer` header first, else the `kukie_access` cookie — validates it via `JwtTokenProvider` and stashes the user id as a request attribute. Header wins when both are present (a blank header counts as absent; a present-but-malformed header is a 401, never a cookie fallback). Cookie auth is accepted only when the browser's `Sec-Fetch-Site` is `same-origin` or `none` — `same-site`, `cross-site` or missing → 403 `CROSS_SITE_COOKIE` (fail-closed; `SameSite=Lax` alone lets top-level GETs through; the web client shares this server's origin via the reverse proxy). Browsers send that header only on HTTPS/localhost, so web login presumes an HTTPS deployment (the cookies are `Secure` anyway). The agent sends Bearer; the web client (in a browser or inside the Electron shell, which loads the same web page) relies on cookies.
- Login/refresh responses return tokens **both** as JSON and as httpOnly cookies (`kukie_access`, `kukie_refresh`; `Secure`, `SameSite=Lax`, `Path=/`, max-age = token expiry) via `AuthCookies` — no per-client branching. `/auth/refresh` reads the refresh token from the body, else from the cookie; `/auth/logout` clears both cookies. Cookie names/`secure` live in `AuthCookieProperties` (`auth.cookie.*`).
- `@AuthUser userId: UUID` handler parameters are resolved by `AuthUserArgumentResolver` (parameter must be `UUID`).
- **Convention enforced by test**: `@AuthUser` may only appear on handlers covered by `@Authenticated` (`AuthAnnotationConventionTest`).
- **Internal API** (agent → `/internal/**`): `@InternalApi` on the class or handler; `InternalApiInterceptor` (registered before `AuthenticationInterceptor`) compares `X-Kukie-Internal-Token` with `internal.token` via `MessageDigest.isEqual` → 401 `INVALID_INTERNAL_TOKEN`. `InternalApiConventionTest` enforces that every handler under `/internal` has `@InternalApi` and no other handler does. The reverse proxy must not expose `/internal` publicly.
- Access/refresh tokens are typed via a `type` claim; refresh tokens are stored in Redis and rotated on refresh. Expirations configured under `jwt.*` in `application.yaml`.

### OAuth (auth feature)

- **The only OAuth client is the web page** (opened in a browser or inside the Electron shell). It uses the authorization code flow with PKCE: the provider redirects the browser to `<origin>/auth/callback` (a front-end route, not an endpoint of this server), and the page POSTs `code` + `redirectUri` (+ `codeVerifier`) to `/auth/oauth/{provider}`, which exchanges the code using the client secret. The old native desktop flow (loopback redirect on `http://127.0.0.1:<port>`, a separate desktop credential pair) is gone.
- **One credential pair per provider** (`OAuthProperties`: `github` and `google` are each a `Credential(clientId, clientSecret)` bound from `oauth.<provider>.client-id/client-secret`). Both are required at startup — any missing or blank value fails startup (`@Validated` + `@NotBlank`; compose passes absent vars as empty strings). The front end's `VITE_*_WEB_CLIENT_ID` must be the same client, or the exchange fails.
- **Google**: a **"Web application" type** client with every callback URI registered (`https://<deployed origin>/auth/callback`, `http://localhost:5173/auth/callback` for local dev).
- **GitHub**: one OAuth App with the same callback URLs registered (an OAuth App can hold several).

### Clusters (cluster feature)

- Every cluster belongs to a team (`team_id NOT NULL`; there are no personal clusters). Paths carry no `{teamId}`, so `ClusterPermission` checks membership in the service: readable by current team members, writable by team ADMINs (`NOT_TEAM_ADMIN`). Unreadable clusters are 404 `NOT_FOUND`, never 403.
- Uploaded kubeconfigs go through the `KubeconfigParser` port (`SnakeYamlKubeconfigParser`, a port of the agent's `kubeconfig.py`): rejects exec/auth-provider, file-path references, non-inline data and private/loopback/link-local/reserved addresses, including what the host name resolves to (`HostResolver`). Rejection messages are user-facing (`KUBECONFIG_REJECTED` carries the reason).
- Name resolution can hang (getaddrinfo has no timeout), so `SystemHostResolver` runs lookups on its own 4 threads (the agent's `RESOLVE_WORKERS`) and waits at most 5 s; a lookup that times out is rejected (an unknown name, NXDOMAIN, resolves to nothing and passes, as in the agent). Register/update parse the kubeconfig **outside any transaction** so no DB connection is held during DNS (this relies on `open-in-view: false`); `UpdateClusterService` checks permission first, then re-reads the cluster and writes in a short `TransactionTemplate` transaction so it never writes back values read before the lookup.
- Credentials are stored only as AES-256-GCM ciphertext (`CredentialCipher`); plaintext leaves the server only via the internal `connection` endpoint. Public responses never include credentials.

### Persistence

- Entities extend `BaseTimeEntity` (JPA auditing `createdAt`/`updatedAt`), use time-ordered UUIDv7 ids (`UuidCreator.getTimeOrderedEpoch()`), `protected set` properties, and encode passwords in the constructor (entity takes `rawPassword` + `PasswordEncoder`).
- Schema is managed by **Flyway** (`src/main/resources/db/migration`, `V<n>__Description.sql`); Hibernate runs with `ddl-auto: validate`, so any entity change needs a migration. Tables are prefixed `tbl_`.
- `spring.jpa.open-in-view` is **false**: a DB connection is borrowed only for a transaction or a single repository call, never for the whole request (slow DNS or mail would otherwise drain the pool). Entities loaded outside a service transaction are detached — do not rely on lazy loading or dirty checking there; put reads and writes that belong together in one transaction.
- Repository "find or throw" helpers are extension functions (e.g. `UserRepository.findByIdOrThrow`).
- Email addresses are `global.domain.Email` (a `@JvmInline value class`; `Email(raw)` trims + lowercases), never `String`, in entities and repositories; request/response DTOs and mail ports keep `String` (`email.value`). The request DTO's `toCommand(...)` does the conversion, so commands carry `Email` and services never wrap request strings (in such DTOs write the validation annotation as `@field:jakarta.validation.constraints.Email` to avoid the name clash). Exceptions: `SendVerificationCodeService` takes a single `Email` built by the controller, and `OAuthLogInService` wraps the provider's profile email. It is stored as a plain `varchar` and values read from the DB are not re-normalized, so the DB must hold only normalized addresses (see V6). Keep JPA annotations on fields: property access would hit the mangled getters (`getEmail-…`).

## Testing

- Test method names are Korean sentences in backticks (`` fun `로그인에 성공하면 토큰을 발급하고 리프레시 토큰을 저장한다`() ``) with `// given` / `// when` / `// then` comments.
- **Test ordering**: within each use case (API/service being tested), the success case comes first, followed by its exception cases.
- **Unit tests** (`*ServiceTest`, `JwtTokenProviderTest`): no Spring context — MockK (`@ExtendWith(MockKExtension::class)` with `@MockK`/`@SpyK`/`@InjectMockKs`) plus kotest assertions (`shouldBe`, `shouldThrow`).
- **Integration tests** (`*IntegrationTest`): extend `support/IntegrationTest`, which boots the full app (`@SpringBootTest` + MockMvc) against Testcontainers and replaces the mail senders with `FakeVerificationCodeSender`/`FakeTeamInvitationSender` (`@Primary`; read sent codes via `lastCodeFor(email)`). It also replaces `HostResolver` with `FakeHostResolver` (fixed public address; set `addresses` to simulate a private one, `onResolve` to act during a lookup) so kubeconfig checks never hit real DNS. The base class provides `mockMvc`, `loggedInUser()` (persists a user and issues real tokens), `savedTeam(userId to role, …)`, `Any.toJson()`, and `authorization(accessToken)` / `internalToken()` DSL helpers, and after each test truncates all tables and flushes Redis — individual tests never clean up. Use the MockMvc Kotlin DSL (`mockMvc.post("/users") { ... }.andExpect { ... }`).
- Entity test data comes from fixture objects with overridable defaults (`UserFixture.user()`).

## Conventions (from CONTRIBUTING.md)

- Git flow without release branches: PRs target `develop`; branch names are `<feat|fix|refactor|hotfix>/<kebab-description>-<issue#>` (e.g. `feat/chat-session-12`).
- Commit messages: `<type>: <subject>` with types `feat|fix|refactor|docs|style|test|chore`, imperative subject, optional scope (`feat(auth): ...`). Commit subjects and code messages are written in Korean.
- Code style follows the official Kotlin Coding Conventions.
- Comments: write one only for a constraint the code cannot show and a reader would likely break without it (e.g. a lock order), as a single short line. Do not restate the code, cite planning docs or issue numbers, or narrate design history. Test `// given` / `// when` / `// then` markers are the exception.
