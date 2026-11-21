package com.duro.kukie.activity.application

import com.duro.kukie.activity.domain.ActiveUserRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RecordActiveUserServiceTest {

    @MockK(relaxUnitFun = true)
    private lateinit var activeUserRepository: ActiveUserRepository

    private val userId = UUID.randomUUID()

    private fun serviceAt(instant: Instant) = RecordActiveUserService(activeUserRepository, Clock.fixed(instant, ZoneOffset.UTC))

    @Test
    fun `인증된 사용자를 활동 사용자로 기록한다`() {
        // given
        val now = Instant.parse("2026-11-21T01:00:10Z")

        // when
        serviceAt(now)(userId)

        // then
        verify(exactly = 1) { activeUserRepository.record(userId, now) }
    }

    @Test
    fun `같은 사용자는 같은 분 안에 한 번만 기록한다`() {
        // given
        val service = serviceAt(Instant.parse("2026-11-21T01:00:10Z"))

        // when
        service(userId)
        service(userId)

        // then
        verify(exactly = 1) { activeUserRepository.record(userId, any()) }
    }

    @Test
    fun `기록에 실패해도 예외를 던지지 않는다`() {
        // given
        every { activeUserRepository.record(any(), any()) } throws IllegalStateException("redis down")

        // when
        serviceAt(Instant.parse("2026-11-21T01:00:10Z"))(userId)

        // then
        verify(exactly = 1) { activeUserRepository.record(userId, any()) }
    }
}
