package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.util.logger
import com.duro.kukie.team.application.TeamMemberInvited
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TeamInvitationMailListener(
    private val teamInvitationSender: TeamInvitationSender,
) {

    private val log = logger()

    /** 초대 메일은 커밋 뒤에 보내고, 발송이 실패해도 초대는 남긴다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TeamMemberInvited) {
        try {
            teamInvitationSender.send(event.email, event.teamName, event.inviterName)
        } catch (exception: Exception) {
            log.error("초대 메일을 보내지 못했다 — 초대는 그대로 남는다: {} ({} 팀)", event.email, event.teamName, exception)
        }
    }
}
