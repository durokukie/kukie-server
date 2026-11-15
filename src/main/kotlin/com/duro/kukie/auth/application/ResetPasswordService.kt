package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.ResetPasswordCommand
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.VerificationType
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResetPasswordService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val verificationCodeRepository: VerificationCodeRepository,
) {

    @Transactional
    operator fun invoke(command: ResetPasswordCommand) {
        val code = verificationCodeRepository.findByEmail(VerificationType.PASSWORD_RESET, command.email)
        if (code == null || code != command.verificationCode) {
            throw InvalidVerificationCodeException()
        }

        val user = userRepository.findByEmail(command.email) ?: throw InvalidVerificationCodeException()
        user.changePassword(command.newPassword, passwordEncoder)

        verificationCodeRepository.deleteByEmail(VerificationType.PASSWORD_RESET, command.email)
    }
}
