package com.duro.kukie.auth.infrastructure

import com.duro.kukie.auth.application.port.out.PasswordResetCodeSender
import com.duro.kukie.global.mail.MailClient
import com.duro.kukie.user.domain.VerificationCodeRepository
import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale

@Component
class MailPasswordResetCodeSender(
    private val mailClient: MailClient,
    private val templateEngine: ITemplateEngine,
) : PasswordResetCodeSender {

    override fun send(email: String, code: String) {
        mailClient.send(
            to = email,
            subject = "[kukie] 비밀번호 재설정 코드입니다.",
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
        private const val TEMPLATE = "mail/password-reset-code"
    }
}
