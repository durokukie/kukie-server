package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.out.GitHubAccountAge
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingTerms
import com.duro.kukie.hosting.exception.HostingAccountTooNewException
import com.duro.kukie.hosting.exception.HostingGitHubNotLinkedException
import com.duro.kukie.hosting.exception.HostingNoticeNotAgreedException
import com.duro.kukie.hosting.presentation.dto.request.JoinHostingRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class JoinHostingService(
    private val hostingApps: HostingApps,
    private val gitHubAccountAge: GitHubAccountAge,
    private val hostingCommandSender: HostingCommandSender,
) {

    /**
     * 호스팅 사용 신청. 가입 조건은 여기서 본다: 연결한 GitHub 계정이 있고, 종료일 안내에 동의했고, 그 계정을 만든 지 30일이 지났다.
     * 통과하면 배포기에 가입 명령을 보내고, 배포기가 그 계정의 팀을 만들면 가입된 것이다(목록의 joined). 배포기는 명령 큐로만
     * 가입을 받고 큐에는 이 서버만 넣으므로, push·업로드·레포 연결 어느 길로도 이 확인을 건너뛸 수 없다.
     * 이미 가입한 계정이 다시 신청해도 달라지는 것은 없다.
     */
    operator fun invoke(userId: UUID, request: JoinHostingRequest): HostingCommandResponse {
        val account = hostingApps.accountOf(userId) ?: throw HostingGitHubNotLinkedException()
        if (!request.agreed) throw HostingNoticeNotAgreedException()

        val eligibleAt = gitHubAccountAge.createdAt(account.id).plus(HostingTerms.MIN_ACCOUNT_AGE)
        // 알려 주는 날은 30일이 되는 시각의 다음 날이다: 그날 언제 신청해도 통과한다
        if (eligibleAt.isAfter(Instant.now())) throw HostingAccountTooNewException(eligibleAt.atZone(HostingTerms.ZONE).toLocalDate().plusDays(1))

        return hostingCommandSender.send(userId, HostingCommand.join(account))
    }
}
