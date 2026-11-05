package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.out.PasswordResetCodeSender
import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.VerificationType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SendPasswordResetCodeServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK(relaxUnitFun = true)
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @MockK(relaxUnitFun = true)
    private lateinit var passwordResetCodeSender: PasswordResetCodeSender

    @InjectMockKs
    private lateinit var sendPasswordResetCodeService: SendPasswordResetCodeService

    private val email = Email(UserFixture.DEFAULT_EMAIL)

    @Test
    fun `가입된 이메일이면 6자리 재설정 코드를 저장하고 같은 코드를 발송한다`() {
        // given
        val savedCode = slot<String>()
        val sentCode = slot<String>()
        every { userRepository.existsByEmail(email) } returns true
        every { verificationCodeRepository.save(VerificationType.PASSWORD_RESET, email, capture(savedCode)) } returns Unit
        every { passwordResetCodeSender.send(email.value, capture(sentCode)) } returns Unit

        // when
        sendPasswordResetCodeService(email)

        // then
        savedCode.captured shouldMatch "\\d{6}"
        sentCode.captured shouldBe savedCode.captured
    }

    @Test
    fun `가입하지 않은 이메일이면 예외 없이 아무것도 보내지 않는다`() {
        // given
        every { userRepository.existsByEmail(email) } returns false

        // when
        sendPasswordResetCodeService(email)

        // then
        verify(exactly = 0) { verificationCodeRepository.save(any(), any(), any()) }
        verify(exactly = 0) { passwordResetCodeSender.send(any(), any()) }
    }
}
