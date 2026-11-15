package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingRepositoryNotFoundException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StartHostingAppService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /**
     * 회원이 GitHub App 에 허용한 레포 가운데 하나로 앱을 시작한다. 그 뒤로는 그 레포에 push 하면 다시 배포된다.
     * 배포기는 누가 요청했는지 보지 않으므로, 회원의 목록에 있는 레포인지 여기서 확인한다.
     */
    operator fun invoke(userId: UUID, repository: String): HostingCommandResponse {
        val account = hostingApps.accountOf(userId) ?: throw HostingRepositoryNotFoundException()
        val repos = hostingApps.repoList(account) ?: throw HostingRepositoryNotFoundException()
        if (!repos.allowed) throw HostingNotAllowedException()
        val repo = repos.repos.firstOrNull { it.fullName.equals(repository, ignoreCase = true) } ?: throw HostingRepositoryNotFoundException()

        return hostingCommandSender.send(userId, HostingCommand.deploy(account, repo.fullName))
    }
}
