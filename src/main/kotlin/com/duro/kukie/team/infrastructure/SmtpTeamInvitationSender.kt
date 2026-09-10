package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

@Component
class SmtpTeamInvitationSender(
    private val mailClient: MailClient,
) : TeamInvitationSender {

    /**
     * 팀 이름과 초대한 사람 이름은 **사용자가 입력한 값**이라 그대로 넣으면 안 된다. MailClient 는 HTML 메일을
     * 보내므로, `<a href="...">` 같은 팀 이름을 지어 두면 우리 발신자 명의로 임의의 링크가 든 메일이 나간다.
     * 제목은 평문이라 그대로 두고, HTML 본문에 들어가는 값만 이스케이프한다.
     */
    override fun send(email: String, teamName: String, inviterName: String) {
        val team = HtmlUtils.htmlEscape(teamName)
        val inviter = HtmlUtils.htmlEscape(inviterName)

        mailClient.send(
            to = email,
            subject = "[kukie] ${inviterName}님이 ${teamName} 팀에 초대했습니다.",
            htmlBody = """
                <h1>${team} 팀 초대</h1>
                <p>${inviter}님이 회원님을 ${team} 팀에 초대했습니다.</p>
                <p>Kukie 에 가입하면 받은 초대함에서 초대를 수락할 수 있습니다.</p>
            """.trimIndent(),
        )
    }
}
