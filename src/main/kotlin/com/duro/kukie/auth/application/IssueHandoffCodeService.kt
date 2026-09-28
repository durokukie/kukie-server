package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.IssueHandoffCodeCommand
import com.duro.kukie.auth.domain.HandoffCodeRepository
import com.duro.kukie.auth.domain.HandoffTicket
import com.duro.kukie.auth.presentation.dto.response.HandoffCodeResponse
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/** 앱에 넘겨줄 1회용 코드를 PKCE 검증값과 함께 발급한다. */
@Service
class IssueHandoffCodeService(
    private val handoffCodeRepository: HandoffCodeRepository,
) {

    operator fun invoke(command: IssueHandoffCodeCommand): HandoffCodeResponse {
        val code = randomCode()
        handoffCodeRepository.save(code, HandoffTicket(command.userId, command.codeChallenge), TTL)

        return HandoffCodeResponse(code, TTL.seconds)
    }

    private fun randomCode(): String {
        val bytes = ByteArray(CODE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        val TTL: Duration = Duration.ofSeconds(60)
        private const val CODE_BYTES = 32
    }
}
