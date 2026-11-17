package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.config.properties.WebAppProperties
import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.team.application.port.out.TeamInvitationLinkCodec
import com.duro.kukie.team.application.port.out.TeamInvitationSender
import com.duro.kukie.team.domain.TeamInvitation
import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale
import java.util.UUID

@Component
class MailTeamInvitationSender(
    private val mailClient: MailClient,
    private val templateEngine: ITemplateEngine,
    private val webAppProperties: WebAppProperties,
    private val teamInvitationLinkCodec: TeamInvitationLinkCodec,
) : TeamInvitationSender {

    override fun send(email: String, teamName: String, inviterName: String, registered: Boolean, invitationId: UUID) {
        mailClient.send(
            to = email,
            subject = "[kukie] ${inviterName}님이 $teamName 팀에 초대했습니다.",
            htmlBody = templateEngine.process(
                TEMPLATE,
                Context(
                    /* locale = */ Locale.KOREA,
                    /* variables = */ mapOf(
                        "email" to email,
                        "teamName" to teamName,
                        "inviterName" to inviterName,
                        "registered" to registered,
                        "expirationDays" to TeamInvitation.VALIDITY.toDays(),
                        "acceptUrl" to acceptUrl(invitationId),
                    ),
                ),
            ),
        )
    }

    /** 초대 id 를 그대로 싣지 않는다 — 초대한 팀의 관리자가 아는 값이라 링크를 만들어 보낼 수 있다. */
    private fun acceptUrl(invitationId: UUID): String =
        webAppProperties.url.trimEnd('/') + "/?go=inbox&invitation=" + teamInvitationLinkCodec.encode(invitationId)

    companion object {
        private const val TEMPLATE = "mail/team-invitation"
    }
}
