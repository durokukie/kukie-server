package com.duro.kukie.user.application

import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

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

    private val request = CreateUserRequest(
        name = "쿠키",
        email = "kukie@example.com",
        password = "password123",
        verificationCode = "123456",
    )

    @Test
    fun `이미 가입된 이메일이면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(request.email) } returns true

        // when & then
        shouldThrow<DuplicatedEmailException> { createUserService.createUser(request) }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `발급된 인증 코드가 없으면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(request.email) } returns false
        every { verificationCodeRepository.findByEmail(request.email) } returns null

        // when & then
        shouldThrow<InvalidVerificationCodeException> { createUserService.createUser(request) }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `인증 코드가 일치하지 않으면 예외가 발생한다`() {
        // given
        every { userRepository.existsByEmail(request.email) } returns false
        every { verificationCodeRepository.findByEmail(request.email) } returns "654321"

        // when & then
        shouldThrow<InvalidVerificationCodeException> { createUserService.createUser(request) }

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `인증 코드가 일치하면 비밀번호를 인코딩해 저장하고 코드를 삭제한다`() {
        // given
        val savedUser = slot<User>()
        every { userRepository.existsByEmail(request.email) } returns false
        every { verificationCodeRepository.findByEmail(request.email) } returns request.verificationCode
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured }

        // when
        createUserService.createUser(request)

        // then
        verify { verificationCodeRepository.deleteByEmail(request.email) }
        with(savedUser.captured) {
            name shouldBe request.name
            email shouldBe request.email
            password shouldNotBe request.password
            passwordEncoder.matches(request.password, password) shouldBe true
        }
    }
}
