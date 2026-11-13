package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.WebAppProperties
import com.duro.kukie.hosting.application.port.out.HostingAgentRateLimit
import com.duro.kukie.hosting.domain.HostingAgentLogin
import com.duro.kukie.hosting.domain.HostingAgentLoginRepository
import com.duro.kukie.hosting.exception.HostingTooManyRequestsException
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginResponse
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class StartHostingAgentLoginService(
    private val hostingAgentLoginRepository: HostingAgentLoginRepository,
    private val hostingAgentRateLimit: HostingAgentRateLimit,
    private val webAppProperties: WebAppProperties,
) {

    private val random = SecureRandom()

    /**
     * 에이전트의 로그인을 시작한다. loginUrl 에는 code 를 넣지 않는다: 남이 만든 링크를 누르기만 해서 허용되지 않게 사용자가 직접 입력한다.
     * ponytail: 서버 전체 1분 120번. 앞단(CloudFront)이 시청자 주소 헤더를 붙이게 되면 IP 마다로 바꾼다.
     */
    operator fun invoke(): HostingAgentLoginResponse {
        if (!hostingAgentRateLimit.tryAcquire("login", LOGINS_PER_MINUTE, Duration.ofMinutes(1))) throw HostingTooManyRequestsException()
        val pollToken = "pt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val requestedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        // 코드가 겹치면(2^40 가운데) 다시 뽑는다. 덮어쓰면 먼저 만든 코드의 허용이 남의 에이전트로 간다
        val code = generateSequence { newCode() }.first { code ->
            hostingAgentLoginRepository.create(HostingAgentLogins.hash(pollToken), HostingAgentLogin(code, HostingAgentLogin.Status.PENDING, requestedAt), LOGIN_TTL)
        }

        return HostingAgentLoginResponse(
            code = HostingAgentLogins.display(code),
            // 웹은 라우터가 없어 ?go= 로 열 화면을 받는다(로그인을 다녀와도 남는다)
            loginUrl = webAppProperties.url.trimEnd('/') + "/?go=agent",
            pollToken = pollToken,
            expiresIn = LOGIN_TTL.seconds,
        )
    }

    private fun newCode(): String =
        (1..HostingAgentLogins.CODE_LENGTH).map { HostingAgentLogins.CODE_ALPHABET[random.nextInt(HostingAgentLogins.CODE_ALPHABET.length)] }.joinToString("")

    companion object {
        val LOGIN_TTL: Duration = Duration.ofMinutes(10)
        private const val LOGINS_PER_MINUTE = 120
    }
}
