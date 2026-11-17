package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.`in`.JoinHostingCommand
import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingTerms
import com.duro.kukie.hosting.exception.HostingAccountTooNewException
import com.duro.kukie.hosting.exception.HostingGitHubLoginChangedException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingNoticeNotAgreedException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class JoinHostingService(
    private val hostingApps: HostingApps,
    private val gitHubAccountAge: GitHubAccountAge,
    private val hostingCommandSender: HostingCommandSender,
) {

    /**
     * 호스팅 사용 신청. 통과하면 배포기에 가입 명령을 보내고, 배포기가 계정의 팀을 만들면 가입된 것이다(목록의 joined).
     * 배포기는 가입을 명령 큐로만 받고 큐에는 이 서버만 넣으므로, 가입 조건은 여기서만 보면 된다.
     */
    operator fun invoke(command: JoinHostingCommand): HostingCommandResponse {
        val account = hostingApps.accountOf(command.userId) ?: throw HostingGitHubNotLinkedException()
        if (!command.agreed) throw HostingNoticeNotAgreedException()

        // 계정마다 1분에 한 번만 GitHub 에 묻는다: 거절돼도 대기 시간은 그대로 둔다
        return hostingCommandSender.send(command.userId, HostingCommand.join(account)) {
            val now = gitHubAccountAge.read(account.id)
            // 팀 이름은 계정명이다. 연결한 뒤 계정명을 바꿨으면 옛 이름은 이제 다른 계정의 것일 수 있다
            if (!now.login.equals(account.login, ignoreCase = true)) throw HostingGitHubLoginChangedException()
            val eligibleAt = now.createdAt.plus(HostingTerms.MIN_ACCOUNT_AGE)
            // 알려 주는 날은 30일이 되는 시각의 다음 날이다: 그날 언제 신청해도 통과한다
            if (eligibleAt.isAfter(Instant.now())) throw HostingAccountTooNewException(eligibleAt.atZone(HostingTerms.ZONE).toLocalDate().plusDays(1))
        }
    }
}
