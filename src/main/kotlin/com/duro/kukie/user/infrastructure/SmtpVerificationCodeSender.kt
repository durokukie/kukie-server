package com.duro.kukie.user.infrastructure

import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.user.application.port.out.VerificationCodeSender
import com.duro.kukie.user.domain.VerificationCodeRepository
import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

@Component
class SmtpVerificationCodeSender(
    private val mailClient: MailClient,
    private val templateEngine: ITemplateEngine,
) : VerificationCodeSender {

    override fun send(email: String, code: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] 이메일 인증 번호입니다.",
            htmlBody = templateEngine.process(
                TEMPLATE,
                Context(
                    /* locale = */ Locale.KOREA,
                    /* variables = */ mapOf(
                        "code" to code,
                        "expirationMinutes" to VerificationCodeRepository.EXPIRATION.toMinutes(),
                    ),
                ),
            ),
        )
    }

    companion object {
        private const val TEMPLATE = "mail/verification-code"
    }
}
