package com.duro.kukie.user.application

import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.application.port.`in`.ChangePasswordCommand
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.exception.InvalidCurrentPasswordException
import com.duro.kukie.user.exception.PasswordNotSetException
import com.duro.kukie.user.exception.SamePasswordException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@ExtendWith(MockKExtension::class)
class ChangePasswordServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @SpyK
    private var passwordEncoder = BCryptPasswordEncoder()

    @InjectMockKs
    private lateinit var changePasswordService: ChangePasswordService

    private val user = UserFixture.user()

    private val command = ChangePasswordCommand(
        userId = user.id,
        currentPassword = UserFixture.DEFAULT_PASSWORD,
        newPassword = "new-password1",
    )

    @Test
    fun `현재 비밀번호가 맞으면 새 비밀번호로 바꾼다`() {
        // given
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when
        changePasswordService(command)

        // then
        user.matchesPassword(command.newPassword, passwordEncoder) shouldBe true
        user.matchesPassword(UserFixture.DEFAULT_PASSWORD, passwordEncoder) shouldBe false
    }

    @Test
    fun `비밀번호가 없는 소셜 로그인 계정이면 예외가 발생한다`() {
        // given
        val oAuthUser = UserFixture.oAuthUser()
        every { userRepository.findByIdOrThrow(oAuthUser.id) } returns oAuthUser

        // when & then
        shouldThrow<PasswordNotSetException> { changePasswordService(command.copy(userId = oAuthUser.id)) }
    }

    @Test
    fun `현재 비밀번호가 틀리면 예외가 발생하고 비밀번호를 바꾸지 않는다`() {
        // given
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when & then
        shouldThrow<InvalidCurrentPasswordException> {
            changePasswordService(command.copy(currentPassword = "wrong-password"))
        }
        user.matchesPassword(UserFixture.DEFAULT_PASSWORD, passwordEncoder) shouldBe true
    }

    @Test
    fun `새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다`() {
        // given
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when & then
        shouldThrow<SamePasswordException> {
            changePasswordService(command.copy(newPassword = UserFixture.DEFAULT_PASSWORD))
        }
    }
}
