package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.global.mail.MailTemplate
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import org.springframework.stereotype.Component

@Component
class SmtpTeamInvitationSender(
    private val mailClient: MailClient,
    private val mailTemplate: MailTemplate,
) : TeamInvitationSender {

    /**
     * 팀 이름과 초대한 사람 이름은 **사용자가 입력한 값**이다. 본문은 HTML 메일로 나가므로 그대로 넣으면
     * `<a href="...">` 같은 팀 이름이 진짜 링크가 되어, 우리 발신자 명의로 임의의 주소를 건 메일을 아무
     * 주소에나 보낼 수 있다. 본문을 템플릿에서 만들어 이스케이프가 기본이 되게 한다.
     *
     * 제목은 `setSubject` 로 평문으로 나가 렌더링되지 않으므로 값을 그대로 쓴다 — 여기서 이스케이프하면
     * `A&B` 팀이 `A&amp;B` 로 보인다.
     */
    override fun send(email: String, teamName: String, inviterName: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] ${inviterName}님이 ${teamName} 팀에 초대했습니다.",
            htmlBody = mailTemplate.render(
                TEMPLATE,
                mapOf("teamName" to teamName, "inviterName" to inviterName),
            ),
        )
    }

    companion object {
        private const val TEMPLATE = "mail/team-invitation"
    }
}
