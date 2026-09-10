package com.duro.kukie.team.infrastructure

import com.duro.kukie.global.mail.MailClient
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

class SmtpTeamInvitationSenderTest {

    private val mailClient = mockk<MailClient>()
    private val sender = SmtpTeamInvitationSender(mailClient)

    @Test
    fun `팀 이름의 마크업은 본문에 그대로 실리지 않는다`() {
        // given — 팀 이름은 사용자 입력이고 초대 메일은 HTML 로 나간다. 그대로 넣으면 우리 발신자
        // 명의로 임의의 링크가 든 메일을 아무 주소에나 보낼 수 있다.
        val htmlBody = slot<String>()
        every { mailClient.send(any(), any(), capture(htmlBody)) } returns Unit

        // when
        sender.send(
            email = "invitee@example.com",
            teamName = """<a href="https://evil.example">계정 확인</a>""",
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

        sender.send(email = "invitee@example.com", teamName = "DURO", inviterName = "복재성")

        htmlBody.captured shouldContain "DURO 팀 초대"
        htmlBody.captured shouldContain "복재성님이"
    }

    @Test
    fun `제목은 평문이라 이스케이프하지 않는다`() {
        val subject = slot<String>()
        every { mailClient.send(any(), capture(subject), any()) } returns Unit

        sender.send(email = "invitee@example.com", teamName = "A&B", inviterName = "복재성")

        subject.captured shouldBe "[kukie] 복재성님이 A&B 팀에 초대했습니다."
    }
}
