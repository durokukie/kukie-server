package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(MockKExtension::class)
class LogOutServiceTest {

    @MockK(relaxUnitFun = true)
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @InjectMockKs
    private lateinit var logOutService: LogOutService

    @Test
    fun `로그아웃하면 리프레시 토큰을 삭제한다`() {
        // given
        val userId = UUID.randomUUID()

        // when
        logOutService(userId)

        // then
        verify { refreshTokenRepository.deleteByUserId(userId) }
    }
}
