package com.duro.kukie.auth.application

import com.duro.kukie.auth.application.port.`in`.ResetPasswordCommand
import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.VerificationType
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockKExtension::class)
class ResetPasswordServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @SpyK
    private var passwordEncoder = BCryptPasswordEncoder()

    @MockK(relaxUnitFun = true)
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @InjectMockKs
    private lateinit var resetPasswordService: ResetPasswordService

    private val command = ResetPasswordCommand(
        email = Email(UserFixture.DEFAULT_EMAIL),
        verificationCode = "123456",
        newPassword = "new-password1",
    )

    @Test
    fun `코드가 맞으면 비밀번호를 바꾸고 코드를 지운다`() {
        // given
        val user = UserFixture.user()
        every { verificationCodeRepository.findByEmail(VerificationType.PASSWORD_RESET, command.email) } returns command.verificationCode
        every { userRepository.findByEmail(command.email) } returns user

        // when
        resetPasswordService(command)

        // then
        user.matchesPassword(command.newPassword, passwordEncoder) shouldBe true
        user.matchesPassword(UserFixture.DEFAULT_PASSWORD, passwordEncoder) shouldBe false
        verify(exactly = 1) { verificationCodeRepository.deleteByEmail(VerificationType.PASSWORD_RESET, command.email) }
    }

    @Test
    fun `코드가 없거나 만료됐으면 예외가 발생한다`() {
        // given
        every { verificationCodeRepository.findByEmail(VerificationType.PASSWORD_RESET, command.email) } returns null

        // when & then
        shouldThrow<InvalidVerificationCodeException> { resetPasswordService(command) }

        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any(), any()) }
    }

    @Test
    fun `코드가 일치하지 않으면 예외가 발생하고 비밀번호를 바꾸지 않는다`() {
        // given
        every { verificationCodeRepository.findByEmail(VerificationType.PASSWORD_RESET, command.email) } returns "654321"

        // when & then
        shouldThrow<InvalidVerificationCodeException> { resetPasswordService(command) }

        verify(exactly = 0) { userRepository.findByEmail(any()) }
        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any(), any()) }
    }
}
