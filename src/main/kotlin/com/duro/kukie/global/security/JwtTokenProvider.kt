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

    fun generateAccessToken(userId: UUID): String =
        generateToken(userId, TokenType.ACCESS, jwtProperties.accessTokenExpiration)

    fun generateRefreshToken(userId: UUID): String =
        generateToken(userId, TokenType.REFRESH, jwtProperties.refreshTokenExpiration)

    fun getUserIdFromAccessToken(token: String): UUID? =
        parseClaims(token)
            .takeIf { it[TOKEN_TYPE] == TokenType.ACCESS.value }
            ?.subject
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun getUserIdFromRefreshToken(token: String): UUID? =
        parseClaims(token)
            .takeIf { it[TOKEN_TYPE] == TokenType.REFRESH.value }
            ?.subject
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun generateToken(userId: UUID, tokenType: TokenType, expiration: Duration): String {
        val now = Instant.now()

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim(TOKEN_TYPE, tokenType.value)
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
    }
}
