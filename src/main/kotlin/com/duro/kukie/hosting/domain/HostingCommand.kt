package com.duro.kukie.hosting.domain

import com.duro.kukie.user.domain.GitHubAccount
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 배포기에 보내는 명령. 삭제는 그사이 같은 이름으로 다시 만든 앱을 지우지 않게 앱의 세대를 함께 보낸다.
 * 배포 시작에는 앱 이름과 세대가 없다. 앱 이름은 배포기가 레포 이름으로 정한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HostingCommand(
    val version: Int,
    val commandId: String,
    val type: String,
    val at: Instant,
    val owner: HostingAccount,
    val repo: HostingRepo,
    val team: String,
    val app: String?,
    val generation: String?,
) {
    /**
     * 같은 요청을 연달아 보냈는지 가릴 때 쓰는 이름. 배포 시작은 계정에 하나다. 레포마다 두면 허용한 레포 수만큼 한꺼번에
     * 보낼 수 있고, 배포기는 명령을 하나씩 처리해서 다른 계정이 그 뒤에서 기다린다.
     */
    val target: String
        @JsonIgnore get() = app ?: NEW_APP

    companion object {
        const val DEPLOY = "deploy"

        // 앱 이름은 밑줄로 시작할 수 없어 겹치지 않는다
        private const val NEW_APP = "_new"
        const val REDEPLOY = "redeploy"
        const val DELETE = "delete"

        fun of(type: String, app: HostingApp) = HostingCommand(
            version = HostingFile.VERSION,
            commandId = UUID.randomUUID().toString(),
            type = type,
            at = Instant.now().truncatedTo(ChronoUnit.SECONDS),
            owner = HostingAccount(app.owner.id),
            repo = HostingRepo(fullName = app.repo.fullName),
            team = app.team,
            app = app.app,
            generation = app.generation,
        )

        fun deploy(account: GitHubAccount, repository: String) = HostingCommand(
            version = HostingFile.VERSION,
            commandId = UUID.randomUUID().toString(),
            type = DEPLOY,
            at = Instant.now().truncatedTo(ChronoUnit.SECONDS),
            owner = HostingAccount(account.id),
            repo = HostingRepo(fullName = repository),
            team = account.login.lowercase(),
            app = null,
            generation = null,
        )
    }
}
