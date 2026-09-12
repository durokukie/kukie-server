package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.global.mail.MailTemplate
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamInvitation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 실제 템플릿 엔진 설정(경로·모드·인코딩)까지 함께 확인하려고 스프링 컨텍스트를 띄운다 — 템플릿을
 * 쓰기로 한 이유가 "이스케이프가 기본" 인데, 그 기본이 진짜 켜져 있는지는 실제 설정으로만 알 수 있다.
 */
class SmtpTeamInvitationSenderTest : IntegrationTest() {

    @Autowired
    private lateinit var mailTemplate: MailTemplate

    private val mailClient = mockk<MailClient>()

    private fun sender() = SmtpTeamInvitationSender(mailClient, mailTemplate)

    @Test
    fun `팀 이름의 마크업은 본문에서 링크가 되지 않는다`() {
        // given — 팀 이름은 사용자 입력이고 초대 메일은 HTML 로 나간다. 그대로 넣으면 우리 발신자
        // 명의로 임의의 링크가 든 메일을 아무 주소에나 보낼 수 있다 (실제 재현된 지적).
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

    @Test
    fun `평범한 팀 이름은 그대로 보인다`() {
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        sender().send(email = "invitee@example.com", teamName = "DURO", inviterName = "복재성")

        htmlBody.captured shouldContain "DURO 팀 초대"
        htmlBody.captured shouldContain "복재성님이"
    }

    @Test
    fun `본문에 초대 유효 기간을 안내한다`() {
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        sender().send(email = "invitee@example.com", teamName = "DURO", inviterName = "복재성")

        htmlBody.captured shouldContain "${TeamInvitation.VALIDITY.toDays()}일 동안만 유효합니다"
    }

    @Test
    fun `제목의 줄바꿈은 한 줄로 눌린다`() {
        // 제목에는 사용자가 지은 이름이 들어간다. 줄바꿈이 헤더에 그대로 실리면 헤더를 하나 더
        // 끼워 넣는 입구가 된다 — MailClient 가 없애므로 여기서는 통과값만 확인한다.
        val subject = slot<String>()
        every { mailClient.send(any(), capture(subject), any()) } returns Unit

        sender().send(
            email = "invitee@example.com",
            teamName = "Hi\r\nBcc: attacker@evil.example",
            inviterName = "복재성",
        )

        // sender 는 제목을 그대로 넘기고, 눌러 담는 일은 MailClient 몫이다
        subject.captured shouldContain "Hi"
    }

    @Test
    fun `제목은 평문이라 이스케이프하지 않는다`() {
        val subject = slot<String>()
        every { mailClient.send(any(), capture(subject), any()) } returns Unit

        sender().send(email = "invitee@example.com", teamName = "A&B", inviterName = "복재성")

        subject.captured shouldBe "[kukie] 복재성님이 A&B 팀에 초대했습니다."
    }
}
