package com.duro.kukie.hosting.application

import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.hosting.application.port.out.HostingStateStore
import com.duro.kukie.hosting.exception.HostingDisabledException
import com.duro.kukie.user.domain.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class HostingAppsTest {

    private val hostingStateStore = mockk<HostingStateStore>()
    private val userRepository = mockk<UserRepository>()

    @Test
    fun `버킷이나 큐를 설정하지 않은 서버에서는 호스팅을 쓸 수 없다`() {
        listOf(
            HostingProperties(),
            HostingProperties(stateBucket = "bucket", commandsQueueUrl = " "),
            HostingProperties(commandsQueueUrl = "https://queue"),
        ).forEach { properties ->
            // given
            val hostingApps = HostingApps(properties, hostingStateStore, userRepository, JsonMapper.builder().build())

            // when & then
            shouldThrow<HostingDisabledException> { hostingApps.accountOf(UUID.randomUUID()) }
            shouldThrow<HostingDisabledException> { hostingApps.requireOwned(UUID.randomUUID(), "shop") }
        }
        verify(exactly = 0) { hostingStateStore.read(any()) }
    }
}
