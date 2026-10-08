package com.duro.kukie.user.application

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.application.port.`in`.CreateUserCommand
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.sql.SQLException

@ExtendWith(MockKExtension::class)
class CreateUserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @SpyK
    private var passwordEncoder = BCryptPasswordEncoder()

    @MockK(relaxUnitFun = true)
    private lateinit var verificationCodeRepository: VerificationCodeRepository

    @InjectMockKs
    private lateinit var createUserService: CreateUserService

    private val command = CreateUserCommand(
        name = UserFixture.DEFAULT_NAME,
        email = Email(UserFixture.DEFAULT_EMAIL),
        password = UserFixture.DEFAULT_PASSWORD,
        verificationCode = "123456",
    )

    @Test
    fun `올바른 인증코드를 입력하고 회원가입에 성공한다`() {
        // given
        val savedUser = slot<User>()
        every { userRepository.existsByEmail(command.email) } returns false
        every { verificationCodeRepository.findByEmail(command.email) } returns command.verificationCode
        every { userRepository.saveAndFlush(capture(savedUser)) } answers { savedUser.captured }

        // when
        createUserService(command)

        // then
        verify(exactly = 1) { userRepository.saveAndFlush(any()) }
        verify(exactly = 1) { verificationCodeRepository.deleteByEmail(command.email) }
        with(savedUser.captured) {
            name shouldBe command.name
            email shouldBe command.email
            matchesPassword(command.password, passwordEncoder) shouldBe true
        }
    }

    @Test
    fun `이미 가입된 이메일이면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(command.email) } returns true

        // when & then
        shouldThrow<DuplicatedEmailException> { createUserService(command) }

        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any()) }
    }

    @Test
    fun `발급된 인증 코드가 없으면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(command.email) } returns false
        every { verificationCodeRepository.findByEmail(command.email) } returns null

        // when & then
        shouldThrow<InvalidVerificationCodeException> { createUserService(command) }

        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any()) }
    }

    @Test
    fun `인증 코드가 일치하지 않으면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(command.email) } returns false
        every { verificationCodeRepository.findByEmail(command.email) } returns "654321"

        // when & then
        shouldThrow<InvalidVerificationCodeException> { createUserService(command) }

        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any()) }
    }

    @Test
    fun `동시 가입으로 저장 시점에 이메일이 중복되면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(command.email) } returns false
        every { verificationCodeRepository.findByEmail(command.email) } returns command.verificationCode
        every { userRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
            "could not execute statement",
            ConstraintViolationException("duplicate key", SQLException(), "tbl_user_email_key"),
        )

        // when & then
        shouldThrow<DuplicatedEmailException> { createUserService(command) }

        verify(exactly = 1) { userRepository.saveAndFlush(any()) }
        verify(exactly = 0) { verificationCodeRepository.deleteByEmail(any()) }
    }
}
