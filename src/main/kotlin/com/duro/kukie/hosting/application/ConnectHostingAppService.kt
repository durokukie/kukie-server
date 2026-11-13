package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.application.port.`in`.ConnectHostingAppCommand
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.exception.HostingAppSourceMismatchException
import com.duro.kukie.hosting.exception.HostingNotAllowedException
import com.duro.kukie.hosting.exception.HostingRepositoryInUseException
import com.duro.kukie.hosting.exception.HostingRepositoryNotFoundException
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import org.springframework.stereotype.Service

@Service
class ConnectHostingAppService(
    private val hostingApps: HostingApps,
    private val hostingCommandSender: HostingCommandSender,
) {

    /** 업로드로 만든 앱에 회원이 GitHub App 에 허용한 레포를 잇는다. 레포 하나에는 앱 하나만 잇는다. */
    operator fun invoke(command: ConnectHostingAppCommand): HostingCommandResponse {
        val owned = hostingApps.requireOwned(command.userId, command.app)
        if (!owned.uploaded) throw HostingAppSourceMismatchException()
        val account = hostingApps.accountOf(command.userId) ?: throw HostingRepositoryNotFoundException()
        val repos = hostingApps.repoList(account) ?: throw HostingRepositoryNotFoundException()
        if (!repos.allowed) throw HostingNotAllowedException()
        val repo = repos.repos.firstOrNull { it.fullName.equals(command.repository, ignoreCase = true) } ?: throw HostingRepositoryNotFoundException()
        // 앱 파일의 레포 이름은 마지막 배포 때 것이라, 그 뒤 이름을 바꾼 레포는 id 로 가린다
        val inUse = hostingApps.ownedBy(account).any { app ->
            app.repo?.let { (repo.id != null && it.id == repo.id) || it.fullName.equals(repo.fullName, ignoreCase = true) } == true
        }
        if (inUse) throw HostingRepositoryInUseException()

        return hostingCommandSender.send(command.userId, HostingCommand.connect(account, owned, repo.fullName))
    }
}
