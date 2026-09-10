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

    /**
     * 메일은 **커밋한 뒤에** 보낸다.
     *
     * 트랜잭션 안에서 보내면 두 가지가 나쁘다. 하나, 보낸 뒤 커밋이 실패하면(예: `uk_team_invitation_pending`
     * 충돌) 존재하지 않는 초대의 메일이 나간다. 둘, SMTP 응답을 기다리는 동안 DB 커넥션을 붙잡고 있다.
     *
     * 실패해도 초대는 남긴다 — SMTP 가 잠깐 죽었다고 관리자가 초대를 아예 못 하게 되는 편이 더 나쁘다.
     * 상대가 가입하면 받은 초대함에서 같은 초대를 보게 된다 (제품기획서 02 §4).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TeamMemberInvited) {
        try {
            teamInvitationSender.send(event.email, event.teamName, event.inviterName)
        } catch (exception: Exception) {
            log.error("초대 메일을 보내지 못했다 — 초대는 그대로 남는다: {} ({} 팀)", event.email, event.teamName, exception)
        }
    }
}
