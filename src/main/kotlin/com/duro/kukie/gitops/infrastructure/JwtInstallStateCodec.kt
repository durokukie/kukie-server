package com.duro.kukie.gitops.infrastructure

import com.duro.kukie.gitops.application.port.out.InstallStateCodec
import com.duro.kukie.gitops.domain.InstallState
import com.duro.kukie.global.config.properties.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/** 로그인 토큰과 같은 키로 서명하지만 type 이 달라 서로 바꿔 쓸 수 없다. */
@Component
class JwtInstallStateCodec(
    jwtProperties: JwtProperties,
) : InstallStateCodec {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    override fun encode(state: InstallState): String =
        Jwts.builder()
            .id(state.nonce)
            .subject(state.teamId.toString())
            .claim(TYPE, TYPE_VALUE)
            .claim(USER_ID, state.userId.toString())
            .expiration(Date.from(state.expiresAt))
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()

    override fun decode(token: String): InstallState? =
        runCatching {
            val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            if (claims[TYPE] != TYPE_VALUE) return null
            InstallState(
                teamId = UUID.fromString(claims.subject),
                userId = UUID.fromString(claims[USER_ID] as String),
                nonce = claims.id ?: return null,
                expiresAt = claims.expiration.toInstant(),
            )
        }.getOrNull()

    companion object {
        private const val TYPE = "type"
        private const val TYPE_VALUE = "github_install"
        private const val USER_ID = "uid"
    }
}
