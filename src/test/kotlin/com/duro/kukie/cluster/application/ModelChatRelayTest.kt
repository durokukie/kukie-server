package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.domain.ModelChatLock
import com.duro.kukie.cluster.exception.ModelChatBusyException
import com.duro.kukie.global.config.properties.ClusterGpuProperties
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ModelChatRelayTest {

    private val gateway = mockk<EksAccessGateway>()
    private val lock = mockk<ModelChatLock>(relaxed = true)
    private val target = ModelChatTarget(
        userId = UUID.randomUUID(),
        clusterId = UUID.randomUUID(),
        awsTarget = AwsTarget("123456789012", "kukie-0123456789abcdef"),
        region = "ap-northeast-2",
        clusterName = "demo",
        endpoint = EksClusterEndpoint("https://example.invalid", ""),
        namespace = "llm",
        name = "gpt-oss-20b",
        model = "openai/gpt-oss-20b",
    )

    @Test
    fun `이미 대화 중인 사용자는 상위를 부르지 않고 거절한다`() {
        // given
        every { lock.tryAcquire(target.userId, any()) } returns null
        val relay = ModelChatRelay(gateway, lock, GpuAuditLog(), ClusterGpuProperties())

        // when & then
        shouldThrow<ModelChatBusyException> { relay.start(target, ByteArray(0)) }
        verify(exactly = 0) { gateway.openModelChat(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `서버의 대화 수 상한을 넘으면 잡은 잠금을 풀고 거절한다`() {
        // given
        every { lock.tryAcquire(target.userId, any()) } returns "token"
        val relay = ModelChatRelay(gateway, lock, GpuAuditLog(), ClusterGpuProperties(chatMaxStreams = 0))

        // when & then
        shouldThrow<ModelChatBusyException> { relay.start(target, ByteArray(0)) }
        verify { lock.release(target.userId, "token") }
        verify(exactly = 0) { gateway.openModelChat(any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
