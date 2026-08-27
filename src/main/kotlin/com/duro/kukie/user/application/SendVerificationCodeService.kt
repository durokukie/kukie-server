package com.duro.kukie.user.application

import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.application.port.VerificationCodeSender
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class SendVerificationCodeService(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val verificationCodeSender: VerificationCodeSender,
) {

    operator fun invoke(email: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicatedEmailException()
        }

        val code = generateCode()
        verificationCodeRepository.save(email, code)

        verificationCodeSender.send(email, code)
    }

    private fun generateCode(): String {
        return Random.nextInt(100000, 1000000).toString()
    }
}
