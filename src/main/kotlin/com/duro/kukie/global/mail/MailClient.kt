package com.duro.kukie.global.mail

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

@Component
class MailClient(
    private val mailSender: JavaMailSender,
) {

    fun send(to: String, subject: String, htmlBody: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.apply {
            setTo(to)
            setSubject(singleLine(subject))
            setText(htmlBody, true)
        }

        mailSender.send(message)
    }

    /**
     * 제목에서 줄바꿈을 없앤다. 제목에는 사용자가 지은 이름이 들어가는데(팀 이름 등), 줄바꿈이 그대로
     * 헤더에 실리면 헤더를 하나 더 끼워 넣는 고전적인 수법의 입구가 된다.
     *
     * 확인해 보니 Jakarta Mail 이 이미 막고 있었다 — 한글이 섞이면 RFC 2047 로 인코딩되어 `=0D=0A` 가
     * 되고, 순수 ASCII 라도 이어지는 줄에 공백을 붙여 접기(folding)로 처리한다. 그래도 여기서 없애는
     * 이유는 그 보장을 **라이브러리 동작이 아니라 우리 코드의 성질로** 만들기 위해서다.
     */
    private fun singleLine(subject: String): String = subject.replace(LINE_BREAKS, " ").trim()

    companion object {
        private val LINE_BREAKS = Regex("[\\r\\n]+")
    }
}
