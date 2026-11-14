package com.duro.kukie.global.security

import com.duro.kukie.auth.exception.ExpiredTokenException
import com.duro.kukie.auth.exception.InvalidTokenException
import com.duro.kukie.global.config.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    /** [sessionId] 는 이 토큰을 낸 로그인. 로그아웃이 그 세션만 끊는 데 쓴다. */
    fun generateAccessToken(userId: UUID, sessionId: String? = null): String =
        generateToken(userId, sessionId, TokenType.ACCESS, jwtProperties.accessTokenExpiration)

    fun generateRefreshToken(userId: UUID, sessionId: String): String =
        generateToken(userId, sessionId, TokenType.REFRESH, jwtProperties.refreshTokenExpiration)

    fun getUserIdFromAccessToken(token: String): UUID? = parseAccessToken(token)?.userId

    fun parseAccessToken(token: String): TokenSubject? = parseByTokenType(token, TokenType.ACCESS)

    /** 세션 도입(DURO-259) 전에 낸 토큰은 sessionId 가 null 이다. */
    fun parseRefreshToken(token: String): TokenSubject? = parseByTokenType(token, TokenType.REFRESH)

    private fun parseByTokenType(token: String, expectedType: TokenType): TokenSubject? {
        val claims = parseClaims(token).takeIf { it[TOKEN_TYPE] == expectedType.value } ?: return null
        val userId = claims.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val sessionId = (claims[SESSION_ID] as? String)?.takeIf { it.isNotBlank() }
        return TokenSubject(userId, sessionId)
    }

    private fun generateToken(userId: UUID, sessionId: String?, tokenType: TokenType, expiration: Duration): String {
        val now = Instant.now()

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim(TOKEN_TYPE, tokenType.value)
            .apply { if (sessionId != null) claim(SESSION_ID, sessionId) }
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiration)))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    private fun parseClaims(token: String): Claims =
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: ExpiredJwtException) {
            throw ExpiredTokenException()
        } catch (_: Exception) {
            throw InvalidTokenException()
        }

    companion object {
        private const val TOKEN_TYPE = "type"
        private const val SESSION_ID = "sid"
    }
}

/** 토큰의 주인과 로그인 세션. */
data class TokenSubject(
    val userId: UUID,
    val sessionId: String?,
)
