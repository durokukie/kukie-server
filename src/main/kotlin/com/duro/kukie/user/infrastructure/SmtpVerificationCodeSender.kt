package com.duro.kukie.user.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.user.application.port.VerificationCodeSender
import org.springframework.stereotype.Component

@Component
class SmtpVerificationCodeSender(
    private val mailClient: MailClient,
) : VerificationCodeSender {

    override fun send(email: String, code: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] 이메일 인증 번호입니다.",
            htmlBody = """
                <h1>이메일 인증 번호</h1>
                <p>아래 인증 번호를 입력하여 가입을 완료해 주세요.</p>
                <h2>$code</h2>
                <p>※ 인증 번호는 3분 동안만 유효합니다.</p>
            """.trimIndent(),
        )
    }
}
