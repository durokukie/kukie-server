package com.duro.kukie.support

import com.duro.kukie.TestcontainersConfig
import com.duro.kukie.auth.domain.RefreshTokenRepository
import com.duro.kukie.global.security.JwtTokenProvider
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper

@ActiveProfiles("test")
@Import(TestcontainersConfig::class, FakeVerificationCodeSenderConfig::class)
@AutoConfigureMockMvc
@SpringBootTest
abstract class IntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var fakeVerificationCodeSender: FakeVerificationCodeSender

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    protected fun loggedInUser(user: User = UserFixture.user()): LoggedInUser {
        val savedUser = userRepository.save(user)
        val accessToken = jwtTokenProvider.generateAccessToken(savedUser.id)
        val refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.id)
        refreshTokenRepository.save(savedUser.id, refreshToken)

        return LoggedInUser(savedUser, accessToken, refreshToken)
    }

    protected fun Any.toJson(): String = objectMapper.writeValueAsString(this)

    protected fun MockHttpServletRequestDsl.authorization(accessToken: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
    }

    @AfterEach
    fun cleanUpStores() {
        val tables = jdbcTemplate.queryForList<String>(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
        )
        if (tables.isNotEmpty()) {
            jdbcTemplate.execute("TRUNCATE ${tables.joinToString(", ")} CASCADE")
        }
        redisTemplate.requiredConnectionFactory.connection.use { it.serverCommands().flushAll() }
        fakeVerificationCodeSender.clear()
    }
}

data class LoggedInUser(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
)
