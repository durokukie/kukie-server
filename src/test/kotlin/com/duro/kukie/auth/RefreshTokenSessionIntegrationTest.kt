package com.duro.kukie.auth

import com.duro.kukie.auth.exception.AuthErrorCode
import com.duro.kukie.auth.infrastructure.RedisRefreshTokenRepository
import com.duro.kukie.auth.presentation.dto.request.LogInRequest
import com.duro.kukie.auth.presentation.dto.request.RefreshTokenRequest
import com.duro.kukie.global.config.properties.JwtProperties
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import com.jayway.jsonpath.JsonPath
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/** DURO-259: 리프레시 토큰을 로그인(세션)마다 둔다. 같은 계정의 두 창·기기가 서로를 끊지 않는다. */
class RefreshTokenSessionIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `같은 계정으로 두 곳에서 로그인해도 서로를 끊지 않는다`() {
        val user = userRepository.save(UserFixture.user())
        var a = logIn(user)
        var b = logIn(user)

        repeat(3) {
            a = refreshOk(a.refresh)
            b = refreshOk(b.refresh)
        }
    }

    @Test
    fun `같은 세션의 옛 토큰을 다시 쓰면 그 세션만 끊긴다`() {
        val user = userRepository.save(UserFixture.user())
        val a = logIn(user)
        val b = logIn(user)
        val aNext = refreshOk(a.refresh)
        passRefreshReuseGrace()

        refresh(a.refresh).andExpect { status { isUnauthorized() } }

        // 재사용을 본 세션은 새 토큰까지 끊긴다
        refresh(aNext.refresh).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value(AuthErrorCode.INVALID_TOKEN.code) }
        }
        // 다른 세션은 그대로다
        refreshOk(b.refresh)
    }

    @Test
    fun `같은 브라우저 두 탭이 동시에 재발급하면 늦은 쪽은 지금 토큰을 받는다`() {
        val user = userRepository.save(UserFixture.user())
        val tokens = logIn(user)

        val first = refreshOk(tokens.refresh)
        val late = refreshOk(tokens.refresh)

        late.refresh shouldBe first.refresh
        refreshOk(late.refresh)
    }

    @Test
    fun `로그아웃은 지금 세션만 끊는다`() {
        val user = userRepository.save(UserFixture.user())
        val a = logIn(user)
        val b = logIn(user)

        mockMvc.delete("/auth/logout") { authorization(a.access) }
            .andExpect { status { isNoContent() } }

        refresh(a.refresh).andExpect { status { isUnauthorized() } }
        refreshOk(b.refresh)
    }

    @Test
    fun `세션 수가 상한을 넘으면 가장 오래 안 쓴 세션부터 끊는다`() {
        val user = userRepository.save(UserFixture.user())
        val sessions = (1..RedisRefreshTokenRepository.MAX_SESSIONS_PER_USER).map { logIn(user) }.toMutableList()
        // 첫 세션을 방금 쓴 것으로 만들면 두 번째가 가장 오래 안 쓴 세션이 된다
        sessions[0] = refreshOk(sessions[0].refresh)

        val extra = logIn(user)

        refresh(sessions[1].refresh).andExpect { status { isUnauthorized() } }
        (sessions - sessions[1] + extra).forEach { refreshOk(it.refresh) }
        redisTemplate.opsForZSet().size("refresh-token:{${user.id}}:sessions") shouldBe
            RedisRefreshTokenRepository.MAX_SESSIONS_PER_USER.toLong()
    }

    @Test
    fun `배포 전에 받은 옛 토큰은 한 번 받아 주고 새 세션으로 옮긴다`() {
        val user = userRepository.save(UserFixture.user())
        val legacy = legacyRefreshToken(user.id)
        redisTemplate.opsForValue().set("refresh-token:${user.id}", legacy)

        val migrated = refreshOk(legacy)

        jwtTokenProvider.parseRefreshToken(migrated.refresh)?.sessionId.shouldNotBeNull()
        refreshOk(migrated.refresh)
        // 옛 키는 겹침 시간 뒤에 사라진다
        redisTemplate.getExpire("refresh-token:${user.id}", TimeUnit.MILLISECONDS) shouldBeLessThanOrEqual
            RedisRefreshTokenRepository.REUSE_GRACE.toMillis()
        redisTemplate.delete("refresh-token:${user.id}")
        refresh(legacy).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `배포 직후 같은 브라우저 두 탭이 같은 옛 토큰으로 동시에 와도 둘 다 옮겨 간다`() {
        val user = userRepository.save(UserFixture.user())
        val legacy = legacyRefreshToken(user.id)
        redisTemplate.opsForValue().set("refresh-token:${user.id}", legacy)

        val first = refreshOk(legacy)
        val second = refreshOk(legacy)

        refreshOk(first.refresh)
        refreshOk(second.refresh)
    }

    @Test
    fun `옛 키와 다른 옛 토큰은 받지 않는다`() {
        val user = userRepository.save(UserFixture.user())
        val stored = legacyRefreshToken(user.id)
        redisTemplate.opsForValue().set("refresh-token:${user.id}", stored)

        refresh(legacyRefreshToken(user.id)).andExpect { status { isUnauthorized() } }

        // 옛 키의 주인은 여전히 옮겨 갈 수 있다
        refreshOk(stored)
    }

    @Test
    fun `세션 id 없는 옛 액세스 토큰으로 로그아웃하면 옛 키만 지우고 새 세션은 둔다`() {
        val user = userRepository.save(UserFixture.user())
        val session = logIn(user)
        val legacy = legacyRefreshToken(user.id)
        redisTemplate.opsForValue().set("refresh-token:${user.id}", legacy)

        mockMvc.delete("/auth/logout") { authorization(jwtTokenProvider.generateAccessToken(user.id)) }
            .andExpect { status { isNoContent() } }

        redisTemplate.hasKey("refresh-token:${user.id}") shouldBe false
        refresh(legacy).andExpect { status { isUnauthorized() } }
        refreshOk(session.refresh)
    }

    @Test
    fun `로그인 응답의 액세스와 리프레시 토큰은 같은 세션이고 로그인마다 다르다`() {
        val user = userRepository.save(UserFixture.user())
        val a = logIn(user)
        val b = logIn(user)

        val aSession = jwtTokenProvider.parseAccessToken(a.access)?.sessionId
        aSession shouldBe jwtTokenProvider.parseRefreshToken(a.refresh)?.sessionId
        aSession shouldNotBe jwtTokenProvider.parseAccessToken(b.access)?.sessionId
        jwtTokenProvider.parseRefreshToken(legacyRefreshToken(user.id))?.sessionId.shouldBeNull()
    }

    private data class Tokens(val access: String, val refresh: String)

    private fun logIn(user: User): Tokens =
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = LogInRequest(user.email.value, UserFixture.DEFAULT_PASSWORD).toJson()
        }.andExpect { status { isOk() } }.tokens()

    private fun refresh(refreshToken: String): ResultActionsDsl =
        mockMvc.post("/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = RefreshTokenRequest(refreshToken).toJson()
        }

    private fun refreshOk(refreshToken: String): Tokens =
        refresh(refreshToken).andExpect { status { isOk() } }.tokens()

    private fun ResultActionsDsl.tokens(): Tokens {
        val body = andReturn().response.contentAsString
        return Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"))
    }

    // 세션 도입 전 서버가 내던 모양: sid 없이 type=refresh
    private fun legacyRefreshToken(userId: UUID): String {
        val now = Instant.now()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(jwtProperties.refreshTokenExpiration)))
            .signWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()), Jwts.SIG.HS256)
            .compact()
    }
}
