package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.out.PasswordResetCodeSender
import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.VerificationType
import org.springframework.stereotype.Service
import kotlin.random.Random

/** 가입 여부를 드러내지 않도록 가입하지 않은 이메일에도 같은 응답을 준다. */
@Service
class SendPasswordResetCodeService(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordResetCodeSender: PasswordResetCodeSender,
) {

    operator fun invoke(email: Email) {
        if (!userRepository.existsByEmail(email)) {
            return
        }

        val code = generateCode()
        verificationCodeRepository.save(VerificationType.PASSWORD_RESET, email, code)

        passwordResetCodeSender.send(email.value, code)
    }

    private fun generateCode(): String {
        return Random.nextInt(0, 1000000).toString().padStart(6, '0')
    }
}
