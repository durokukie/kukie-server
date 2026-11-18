package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.hosting.application.port.out.HostingCommandQueue
import com.duro.kukie.hosting.domain.HostingCommand
import com.duro.kukie.hosting.domain.HostingIdentity
import com.duro.kukie.user.application.UserWithdrawn
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class HostingWithdrawalListener(
    private val hostingProperties: HostingProperties,
    private val hostingApps: HostingApps,
    private val hostingCommandQueue: HostingCommandQueue,
) {

    private val log = logger()

    /** 탈퇴한 회원의 앱마다 삭제 명령을 보낸다. 상태 파일 읽기·큐 넣기는 트랜잭션 밖이라 커밋 뒤에 한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: UserWithdrawn) {
        if (!hostingProperties.enabled) return
        // 실패해도 탈퇴는 되돌리지 않는다 — 그 앱은 남으므로 로그를 보고 사람이 지운다
        val account = HostingIdentity.of(event.userId, event.gitHub)
        val apps = try {
            hostingApps.ownedBy(account)
        } catch (exception: Exception) {
            log.error("탈퇴한 회원의 호스팅 앱을 읽지 못했다 — 앱이 남는다: user={}, team={}", event.userId, account.team, exception)
            return
        }
        apps.forEach { app ->
            try {
                // 방금 회원이 직접 보낸 삭제와 겹쳐도 되므로 HostingCommandSender 의 재요청 막기를 거치지 않는다
                val command = HostingCommand.of(HostingCommand.DELETE, app)
                hostingCommandQueue.send(command)
                log.info("탈퇴한 회원의 호스팅 앱 삭제 명령을 보냈습니다: user={}, app={}/{}, command={}", event.userId, app.team, app.app, command.commandId)
            } catch (exception: Exception) {
                log.error("탈퇴한 회원의 호스팅 앱 삭제 명령을 보내지 못했다 — 앱이 남는다: user={}, app={}/{}", event.userId, app.team, app.app, exception)
            }
        }
    }
}
