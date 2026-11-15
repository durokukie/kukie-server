package com.duro.kukie.cluster.application

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class GpuAuditLogTest {

    private val auditLog = GpuAuditLog()
    private val logger = LoggerFactory.getLogger(GpuAuditLog::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private val userId = UUID.randomUUID()
    private val clusterId = UUID.randomUUID()

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    @Test
    fun `성공하면 사용자와 클러스터와 동작을 남긴다`() {
        // when
        val result = auditLog.record(userId, clusterId, "scale-node-group name=gpu desired=1") { "ok" }

        // then
        result shouldBe "ok"
        appender.list.single().run {
            level shouldBe Level.INFO
            formattedMessage shouldBe "GPU 조작 완료: user=$userId cluster=$clusterId action=scale-node-group name=gpu desired=1"
        }
    }

    @Test
    fun `실패해도 오류 코드만 남기고 예외는 그대로 던진다`() {
        // when
        shouldThrow<NodeGroupBusyException> {
            auditLog.record(userId, clusterId, "scale-node-group name=gpu desired=1") { throw NodeGroupBusyException() }
        }

        // then
        appender.list.single().run {
            level shouldBe Level.WARN
            formattedMessage shouldBe
                "GPU 조작 실패: user=$userId cluster=$clusterId action=scale-node-group name=gpu desired=1 reason=NODE_GROUP_BUSY"
        }
    }

    @Test
    fun `비즈니스 예외가 아니면 메시지를 빼고 예외 이름만 남긴다`() {
        // when
        shouldThrow<IllegalStateException> {
            auditLog.record(userId, clusterId, "stop-model") { throw IllegalStateException("k8s-aws-v1.secret-token") }
        }

        // then
        appender.list.single().formattedMessage shouldBe
            "GPU 조작 실패: user=$userId cluster=$clusterId action=stop-model reason=IllegalStateException"
    }
}
