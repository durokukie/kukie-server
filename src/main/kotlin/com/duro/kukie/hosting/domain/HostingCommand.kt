package com.duro.kukie.hosting.domain

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 배포기에 보내는 명령. 삭제는 그사이 같은 이름으로 다시 만든 앱을 지우지 않게 앱의 세대를 함께 보낸다. */
data class HostingCommand(
    val version: Int,
    val commandId: String,
    val type: String,
    val at: Instant,
    val owner: HostingAccount,
    val repo: HostingRepo,
    val team: String,
    val app: String,
    val generation: String,
) {
    companion object {
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
    }
}
