package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import org.springframework.stereotype.Component

@Component
class SmtpTeamInvitationSender(
    private val mailClient: MailClient,
) : TeamInvitationSender {

    override fun send(email: String, teamName: String, inviterName: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] ${inviterName}님이 ${teamName} 팀에 초대했습니다.",
            htmlBody = """
                <h1>${teamName} 팀 초대</h1>
                <p>${inviterName}님이 회원님을 ${teamName} 팀에 초대했습니다.</p>
                <p>Kukie 에 가입하면 받은 초대함에서 초대를 수락할 수 있습니다.</p>
            """.trimIndent(),
        )
    }
}
