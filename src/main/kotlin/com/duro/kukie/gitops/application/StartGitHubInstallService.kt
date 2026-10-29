package com.duro.kukie.gitops.application

import com.duro.kukie.gitops.application.port.out.InstallStateCodec
import com.duro.kukie.gitops.domain.InstallState
import com.duro.kukie.gitops.domain.InstallStateRepository
import com.duro.kukie.gitops.presentation.dto.response.GitHubInstallLinkResponse
import com.duro.kukie.global.config.properties.GitHubAppProperties
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

/** 팀에 GitHub App 을 설치할 GitHub 링크를 준다. state 는 서명했고 한 번만 쓸 수 있다. */
@Service
class StartGitHubInstallService(
    private val installStateCodec: InstallStateCodec,
    private val installStateRepository: InstallStateRepository,
    private val gitHubAppProperties: GitHubAppProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    operator fun invoke(teamId: UUID, userId: UUID): GitHubInstallLinkResponse {
        val nonce = HexFormat.of().formatHex(ByteArray(NONCE_BYTES).also(random::nextBytes))
        val expiresAt = clock.instant().plus(STATE_TTL).truncatedTo(ChronoUnit.SECONDS)
        val state = InstallState(teamId = teamId, userId = userId, nonce = nonce, expiresAt = expiresAt)
        installStateRepository.save(nonce, teamId, STATE_TTL)

        return GitHubInstallLinkResponse(
            installUrl = gitHubAppProperties.installUrl(installStateCodec.encode(state)),
            expiresAt = expiresAt,
        )
    }

    companion object {
        private const val NONCE_BYTES = 16
        private val STATE_TTL: Duration = Duration.ofMinutes(30)
        private val random = SecureRandom()
    }
}
