package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.config.properties.JwtProperties
import com.duro.kukie.global.security.JwtTokenProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class JwtTeamInvitationLinkCodecTest {

    private val jwtProperties = JwtProperties(
        secret = "jwt-secret-key-must-be-at-least-32-bytes",
        accessTokenExpiration = Duration.ofMinutes(30),
        refreshTokenExpiration = Duration.ofDays(30),
    )

    private val codec = JwtTeamInvitationLinkCodec(jwtProperties)

    @Test
    fun `초대 id 를 서명한 토큰은 같은 id 로 풀린다`() {
        val invitationId = UUID.randomUUID()

        codec.decode(codec.encode(invitationId)) shouldBe invitationId
    }

    @Test
    fun `로그인 토큰은 초대 링크로 풀리지 않는다`() {
        val accessToken = JwtTokenProvider(jwtProperties).generateAccessToken(UUID.randomUUID())

        codec.decode(accessToken).shouldBeNull()
    }
}
