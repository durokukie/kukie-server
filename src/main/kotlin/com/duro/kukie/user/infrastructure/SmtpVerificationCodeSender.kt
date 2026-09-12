package com.duro.kukie.user.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.global.mail.MailTemplate
import com.duro.kukie.user.application.port.out.VerificationCodeSender
import com.duro.kukie.user.domain.VerificationCodeRepository
import org.springframework.stereotype.Component

@Component
class SmtpVerificationCodeSender(
    private val mailClient: MailClient,
    private val mailTemplate: MailTemplate,
) : VerificationCodeSender {

    /** 여기 들어가는 값은 서버가 만든 것뿐이라 주입 여지가 없지만, 메일 본문은 한 방식으로 만든다. */
    override fun send(email: String, code: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] 이메일 인증 번호입니다.",
            htmlBody = mailTemplate.render(
                TEMPLATE,
                mapOf(
                    "code" to code,
                    "expirationMinutes" to VerificationCodeRepository.EXPIRATION.toMinutes(),
                ),
            ),
        )
    }

    companion object {
        private const val TEMPLATE = "mail/verification-code"
    }
}
