package com.duro.kukie.user.application

import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class SendVerificationCodeService(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val mailSender: JavaMailSender,
) {

    @Transactional(readOnly = true)
    operator fun invoke(email: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicatedEmailException()
        }

        val code = generateCode()
        verificationCodeRepository.save(email, code)

        sendEmail(email, code)
    }

    private fun generateCode(): String {
        return Random.nextInt(100000, 1000000).toString()
    }

    private fun sendEmail(email: String, code: String) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.setTo(email)
        helper.setSubject("[kukie] 이메일 인증 번호입니다.")
        helper.setText(
            """
            <h1>이메일 인증 번호</h1>
            <p>아래 인증 번호를 입력하여 가입을 완료해 주세요.</p>
            <h2>$code</h2>
            <p>※ 인증 번호는 3분 동안만 유효합니다.</p>
            """.trimIndent(),
            true
        )

        mailSender.send(message)
    }
}
