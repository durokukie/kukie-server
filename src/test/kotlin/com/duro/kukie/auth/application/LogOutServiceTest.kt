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

    private val userId = UUID.randomUUID()

    @Test
    fun `로그아웃하면 지금 세션의 리프레시 토큰만 삭제한다`() {
        logOutService(userId, "session-1")

        verify { refreshTokenRepository.delete(userId, "session-1") }
        verify(exactly = 0) { refreshTokenRepository.deleteLegacy(any()) }
    }

    @Test
    fun `세션 id 가 없는 옛 액세스 토큰으로 로그아웃하면 옛 키를 지운다`() {
        logOutService(userId, null)

        verify { refreshTokenRepository.deleteLegacy(userId) }
        verify(exactly = 0) { refreshTokenRepository.delete(any(), any()) }
    }
}
