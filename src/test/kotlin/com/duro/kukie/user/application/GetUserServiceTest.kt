package com.duro.kukie.user.application

import com.duro.kukie.user.UserFixture
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
class GetUserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var getUserService: GetUserService

    @Test
    fun `로그인 한 유저의 정보를 정상적으로 조회한다`() {
        // given
        val user = UserFixture.user()
        every { userRepository.findByIdOrThrow(user.id) } returns user

        // when
        val response = getUserService(user.id)

        // then
        with(response) {
            id shouldBe user.id
            name shouldBe user.name
            email shouldBe user.email
        }
    }

    @Test
    fun `존재하지 않는 유저의 정보를 조회하면 예외가 발생한다`() {
        // given
        every { userRepository.findByIdOrThrow(any()) } throws UserNotFoundException()

        // when & then
        shouldThrow<UserNotFoundException> { getUserService(UUID.randomUUID()) }
    }
}
