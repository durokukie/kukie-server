package com.duro.kukie.user.application

import com.duro.kukie.user.UserFixture
import com.duro.kukie.user.application.port.`in`.UpdateUserCommand
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.exception.UserNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class UpdateUserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var updateUserService: UpdateUserService

    @Test
    fun `로그인 한 유저의 이름을 변경한다`() {
        // given
        val user = UserFixture.user()
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when
        val response = updateUserService(UpdateUserCommand(userId = user.id, name = "새 이름"))

        // then
        user.name shouldBe "새 이름"
        with(response) {
            id shouldBe user.id
            name shouldBe "새 이름"
            email shouldBe user.email.value
        }
    }

    @Test
    fun `존재하지 않는 유저의 이름을 변경하면 예외가 발생한다`() {
        // given
        every { userRepository.findByIdOrThrow(any()) } throws UserNotFoundException()

        // when & then
        shouldThrow<UserNotFoundException> {
            updateUserService(UpdateUserCommand(userId = UUID.randomUUID(), name = "새 이름"))
        }
    }
}
