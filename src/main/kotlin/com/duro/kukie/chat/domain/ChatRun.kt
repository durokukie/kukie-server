package com.duro.kukie.chat.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.global.util.withoutNul
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "tbl_chat_run")
class ChatRun(
    sessionId: UUID,
    requestId: String,
    turnNo: Int,
    kind: RunKind,
    mode: String,
    inputText: String?,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(name = "session_id", nullable = false)
    var sessionId = sessionId
        protected set

    @Column(name = "request_id", nullable = false, length = 64)
    var requestId = requestId
        protected set

    @Column(name = "turn_no", nullable = false)
    var turnNo = turnNo
        protected set

    @Column(nullable = false, length = 20)
    var kind = kind
        protected set

    @Column(nullable = false, length = 20)
    var mode = mode
        protected set

    @Column(nullable = false, length = 20)
    var status = RunStatus.RUNNING
        protected set

    @Column(name = "input_text", columnDefinition = "text")
    var inputText = inputText
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    var responsePayload: Map<String, Any?>? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agent_messages")
    var agentMessages: List<Any?>? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_summary")
    var usageSummary: Map<String, Any?>? = null
        protected set

    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime = utcNow()
        protected set

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null
        protected set

    val isActive: Boolean
        get() = status.active

    val isError: Boolean
        get() = responsePayload?.get("kind") == ERROR_KIND

    fun isReplayOf(kind: RunKind, inputText: String?): Boolean =
        this.kind == kind && this.inputText == inputText

    fun recordResponse(payload: Map<String, Any?>) {
        responsePayload = payload.withoutNul()
    }

    fun appendAgentMessages(messages: List<Any?>) {
        agentMessages = agentMessages.orEmpty() + messages.withoutNul()
    }

    fun recordUsage(summary: Map<String, Any?>) {
        usageSummary = summary.withoutNul()
    }

    fun changeStatus(status: RunStatus) {
        this.status = status
        finishedAt = if (status.active) null else utcNow()
    }

    fun interrupt(message: String) {
        if (!isError) {
            responsePayload = linkedMapOf(
                "kind" to ERROR_KIND,
                "code" to INTERRUPTED_CODE,
                "message" to message.withoutNul(),
                "http_status" to INTERRUPTED_HTTP_STATUS,
            )
        }
        changeStatus(RunStatus.INTERRUPTED)
    }

    companion object {
        const val ERROR_KIND = "error"
        private const val INTERRUPTED_CODE = "INTERRUPTED"
        private const val INTERRUPTED_HTTP_STATUS = 409
    }
}
