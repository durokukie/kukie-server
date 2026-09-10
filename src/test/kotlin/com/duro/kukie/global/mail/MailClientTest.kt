package com.duro.kukie.global.mail

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl

class MailClientTest {

    private val realSender = JavaMailSenderImpl().apply { host = "localhost" }
    private val mailSender = mockk<JavaMailSenderImpl>()
    private val client = MailClient(mailSender)

    @Test
    fun `제목의 줄바꿈은 한 칸으로 눌리고 헤더가 갈라지지 않는다`() {
        // given — 제목에는 팀 이름 같은 사용자 입력이 들어간다. 줄바꿈이 헤더에 실리면 헤더를 하나 더
        // 끼워 넣는 입구가 된다.
        val sent = slot<MimeMessage>()
        every { mailSender.createMimeMessage() } returns realSender.createMimeMessage()
        every { mailSender.send(capture(sent)) } returns Unit

        // when
        client.send(
            to = "victim@example.com",
            subject = "[kukie] Hi\r\nBcc: attacker@evil.example 팀에 초대했습니다.",
            htmlBody = "<p>본문</p>",
        )

        // then — 한 줄로 눌리고, Bcc 헤더는 생기지 않는다
        sent.captured.subject shouldBe "[kukie] Hi Bcc: attacker@evil.example 팀에 초대했습니다."
        sent.captured.getHeader("Bcc") shouldBe null
    }
}
