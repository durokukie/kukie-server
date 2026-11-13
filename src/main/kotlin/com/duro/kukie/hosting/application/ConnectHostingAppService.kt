package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingRepositoryInUseException
import com.duro.kukie.hosting.exception.HostingRepositoryNotFoundException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConnectHostingAppService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /**
     * 업로드로 만든 앱에 회원이 GitHub App 에 허용한 레포를 잇는다. 그 뒤로는 그 레포의 기본 브랜치에 push 하면 앱 이름과 주소 그대로
     * 배포된다. 배포기는 누가 요청했는지 보지 않으므로 앱과 레포가 회원의 것인지, 레포가 이미 다른 앱에 이어져 있지 않은지 여기서 본다.
     * 배포기도 레포 id 로 한 번 더 확인한다.
     */
    operator fun invoke(userId: UUID, app: String, repository: String): HostingCommandResponse {
        val owned = hostingApps.requireOwned(userId, app)
        if (!owned.uploaded) throw HostingAppSourceMismatchException()
        val account = hostingApps.accountOf(userId) ?: throw HostingRepositoryNotFoundException()
        val repos = hostingApps.repoList(account) ?: throw HostingRepositoryNotFoundException()
        if (!repos.allowed) throw HostingNotAllowedException()
        val repo = repos.repos.firstOrNull { it.fullName.equals(repository, ignoreCase = true) } ?: throw HostingRepositoryNotFoundException()
        if (hostingApps.ownedBy(account).any { it.repo?.fullName.equals(repo.fullName, ignoreCase = true) }) throw HostingRepositoryInUseException()

        return hostingCommandSender.send(userId, HostingCommand.connect(account, owned, repo.fullName))
    }
}
