package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.config.properties.JwtProperties
import com.duro.kukie.team.application.port.out.TeamInvitationLinkCodec
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.UUID
import javax.crypto.SecretKey

/** 로그인 토큰과 같은 키로 서명하지만 type 이 달라 서로 바꿔 쓸 수 없다. 만료는 초대의 기한이 정하므로 넣지 않는다. */
@Component
class JwtTeamInvitationLinkCodec(
    jwtProperties: JwtProperties,
) : TeamInvitationLinkCodec {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())

    override fun encode(invitationId: UUID): String =
        Jwts.builder()
            .subject(invitationId.toString())
            .claim(TYPE, TYPE_VALUE)
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()

    override fun decode(token: String): UUID? =
        runCatching {
            val claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            if (claims[TYPE] != TYPE_VALUE) return null
            UUID.fromString(claims.subject)
        }.getOrNull()

    companion object {
        private const val TYPE = "type"
        private const val TYPE_VALUE = "team_invitation"
    }
}
