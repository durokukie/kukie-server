package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.support.IntegrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.thymeleaf.ITemplateEngine

/** 실제 템플릿 엔진 설정으로 확인하려고 스프링 컨텍스트를 띄운다. */
class SmtpTeamInvitationSenderTest : IntegrationTest() {

    @Autowired
    private lateinit var templateEngine: ITemplateEngine

    private val mailClient = mockk<MailClient>()

    private fun sender() = SmtpTeamInvitationSender(mailClient, templateEngine)

    @Test
    fun `팀 이름의 마크업은 본문에서 링크가 되지 않는다`() {
        // given
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        // when
        sender().send(
            email = "invitee@example.com",
            teamName = """<a href="https://evil.example">계정 확인하기</a>""",
            inviterName = "초대한사람",
        )

        // then
        htmlBody.captured shouldNotContain "<a href"
        htmlBody.captured shouldContain "&lt;a href"
    }
}
