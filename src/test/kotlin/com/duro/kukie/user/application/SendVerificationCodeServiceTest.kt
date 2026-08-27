package com.duro.kukie.user.application

import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.application.port.VerificationCodeSender
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import io.kotest.assertions.throwables.shouldThrow
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
class SendVerificationCodeServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK(relaxUnitFun = true)
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @MockK(relaxUnitFun = true)
    private lateinit var verificationCodeSender: VerificationCodeSender

    @InjectMockKs
    private lateinit var sendVerificationCodeService: SendVerificationCodeService

    private val email = UserFixture.DEFAULT_EMAIL

    @Test
    fun `6자리 인증 코드를 저장하고 같은 코드를 발송한다`() {
        // given
        val savedCode = slot<String>()
        val sentCode = slot<String>()
        every { userRepository.existsByEmail(email) } returns false
        every { verificationCodeRepository.save(email, capture(savedCode)) } returns Unit
        every { verificationCodeSender.send(email, capture(sentCode)) } returns Unit

        // when
        sendVerificationCodeService(email)

        // then
        savedCode.captured shouldMatch "\\d{6}"
        sentCode.captured shouldBe savedCode.captured
    }

    @Test
    fun `이미 가입된 이메일이면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(email) } returns true

        // when & then
        shouldThrow<DuplicatedEmailException> { sendVerificationCodeService(email) }

        verify(exactly = 0) { verificationCodeSender.send(any(), any()) }
    }
}
