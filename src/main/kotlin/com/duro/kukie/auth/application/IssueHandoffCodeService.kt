package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.HandoffCodeRepository
import com.duro.kukie.auth.domain.HandoffTicket
import com.duro.kukie.auth.presentation.dto.response.HandoffCodeResponse
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * 로그인된 웹 페이지(시스템 브라우저)가 앱에 건넬 1회용 코드를 받는다 (DURO-109).
 * 앱이 시작할 때 맡긴 PKCE 검증값을 같이 저장해 두고, 교환 때 원본(verifier)과 대조한다.
 */
@Service
class IssueHandoffCodeService(
    private val handoffCodeRepository: HandoffCodeRepository,
) {

    operator fun invoke(userId: UUID, codeChallenge: String): HandoffCodeResponse {
        val code = randomCode()
        handoffCodeRepository.save(code, HandoffTicket(userId, codeChallenge), TTL)

        return HandoffCodeResponse(code, TTL.seconds)
    }

    private fun randomCode(): String {
        val bytes = ByteArray(CODE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        /** 브라우저 → OS → 앱으로 건너가는 데 몇 초면 된다. 길게 두면 흘린 코드가 그만큼 오래 산다 */
        val TTL: Duration = Duration.ofSeconds(60)
        private const val CODE_BYTES = 32
    }
}
