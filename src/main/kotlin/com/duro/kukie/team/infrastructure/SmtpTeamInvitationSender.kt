package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import com.duro.kukie.team.domain.TeamInvitation
import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

@Component
class SmtpTeamInvitationSender(
    private val mailClient: MailClient,
    private val templateEngine: ITemplateEngine,
) : TeamInvitationSender {

    override fun send(email: String, teamName: String, inviterName: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] ${inviterName}님이 $teamName 팀에 초대했습니다.",
            htmlBody = templateEngine.process(
                TEMPLATE,
                Context(
                    /* locale = */ Locale.KOREA,
                    /* variables = */ mapOf(
                        "teamName" to teamName,
                        "inviterName" to inviterName,
                        "expirationDays" to TeamInvitation.VALIDITY.toDays(),
                    ),
                ),
            ),
        )
    }

    companion object {
        private const val TEMPLATE = "mail/team-invitation"
    }
}
