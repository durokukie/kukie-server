package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.config.properties.WebAppProperties
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
class MailTeamInvitationSenderTest : IntegrationTest() {

    @Autowired
    private lateinit var templateEngine: ITemplateEngine

    @Autowired
    private lateinit var webAppProperties: WebAppProperties

    private val mailClient = mockk<MailClient>()

    private fun sender() = MailTeamInvitationSender(mailClient, templateEngine, webAppProperties)

    @Test
    fun `가입한 사람에게는 로그인해서 수락하라고 안내한다`() {
        // given
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        // when
        sender().send(email = "invitee@example.com", teamName = "팀", inviterName = "초대한사람", registered = true)

        // then
        htmlBody.captured shouldContain "kukie에 로그인하면"
        htmlBody.captured shouldContain "kukie 열기"
    }

    @Test
    fun `가입하지 않은 사람에게는 가입해서 수락하라고 안내한다`() {
        // given
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        // when
        sender().send(email = "invitee@example.com", teamName = "팀", inviterName = "초대한사람", registered = false)

        // then
        htmlBody.captured shouldContain "kukie에 가입하면"
        htmlBody.captured shouldContain "kukie 시작하기"
    }

    @Test
    fun `팀 이름의 마크업은 본문에서 링크가 되지 않는다`() {
        // given
        val teamName = """<a href="https://evil.example">계정 확인하기</a>"""
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        // when
        sender().send(
            email = "invitee@example.com",
            teamName = teamName,
            inviterName = "초대한사람",
            registered = false,
        )

        // then
        htmlBody.captured shouldNotContain teamName
        htmlBody.captured shouldContain "&lt;a href"
    }
}
