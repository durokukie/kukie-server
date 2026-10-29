package com.duro.kukie.actionplan.domain

import com.duro.kukie.actionplan.exception.PlanCallMismatchException
import com.duro.kukie.actionplan.exception.PlanStateConflictException
import com.duro.kukie.global.entity.BaseTimeEntity
import com.duro.kukie.global.util.withoutNul
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Entity
@Table(name = "tbl_action_plan")
class ActionPlan(
    code: String,
    runId: UUID,
    toolCallId: String,
    toolName: String,
    risk: PlanRisk,
    payload: Map<String, Any?>,
    requestHash: String,
) : BaseTimeEntity() {

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(nullable = false, length = 80)
    var code = code
        protected set

    @Column(name = "run_id", nullable = false)
    var runId = runId
        protected set

    @Column(name = "tool_call_id", nullable = false, length = 64)
    var toolCallId = toolCallId
        protected set

    @Column(name = "tool_name", nullable = false, length = 50)
    var toolName = toolName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status = PlanStatus.DRAFT
        protected set

    @Column(nullable = false, length = 20)
    var risk = risk
        protected set

    // dry-run·offer 를 건너뛰지 못하게 만들 때 함께 온 예행 결과와 판단 가이드는 비운다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var payload: Map<String, Any?> = payload.withoutNul() + mapOf(DRY_RUN_RESULT to null, DECISION_GUIDANCE to null)
        protected set

    @Column(name = "request_hash", nullable = false, length = 64)
    var requestHash = requestHash
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    var decision: Map<String, Any?>? = null
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_result")
    var executionResult: Map<String, Any?>? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    var failureReason: PlanFailureReason? = null
        protected set

    @Column(name = "applied_at")
    var appliedAt: LocalDateTime? = null
        protected set

    val isApproved: Boolean
        get() = decision?.get("approved") == true

    val title: String
        get() = (payload["intent"] as? String)?.trim()?.ifEmpty { null } ?: toolName

    private val isRejected: Boolean
        get() = decision?.get("approved") == false

    private val dryRunResult: Map<*, *>?
        get() = payload[DRY_RUN_RESULT] as? Map<*, *>

    private val dryRunPassed: Boolean
        get() = DryRunStatus.fromOrNull(dryRunResult?.get("status") as? String)?.passed == true

    private val decisionGuidance: String?
        get() = payload[DECISION_GUIDANCE] as? String

    fun recordDryRun(result: DryRunStatus, stdout: String, stderr: String) {
        requireState(status == PlanStatus.DRAFT && dryRunResult == null)
        payload = payload + (
            DRY_RUN_RESULT to linkedMapOf(
                "status" to result.value,
                "stdout" to stdout.withoutNul(),
                "stderr" to stderr.withoutNul(),
                "at" to now().wire(),
            )
        )
        if (result == DryRunStatus.FAILED) {
            markFailed(PlanFailureReason.DRY_RUN_FAILED)
        }
    }

    fun offer(guidance: String, pullRequest: PlanPullRequest? = null) {
        requireState(status == PlanStatus.DRAFT && dryRunPassed && decisionGuidance == null)
        payload = payload + (DECISION_GUIDANCE to guidance.withoutNul())
        if (pullRequest != null) {
            payload = payload + (PULL_REQUEST to linkedMapOf("number" to pullRequest.number, "url" to pullRequest.url.withoutNul()))
        }
        status = PlanStatus.WAITING_APPROVAL
    }

    fun approve(call: PlanCall, userId: UUID) {
        requireState(
            status in DECIDABLE &&
                dryRunPassed &&
                !decisionGuidance.isNullOrEmpty() &&
                decision == null &&
                executionResult == null,
        )
        val mismatched = mismatchedFields(call)
        if (mismatched.isNotEmpty()) throw PlanCallMismatchException(mismatched)

        decide(approved = true, userId = userId)
    }

    fun reject(userId: UUID) {
        if (status == PlanStatus.REJECTED && isRejected) return
        requireState(status in DECIDABLE && decision == null && executionResult == null)

        decide(approved = false, userId = userId)
    }

    fun start() {
        requireState(status == PlanStatus.APPROVED)
        status = PlanStatus.EXECUTING
    }

    fun recordResult(
        success: Boolean,
        stdout: String,
        stderr: String,
        exitCode: Int?,
        mergeCommit: String? = null,
        syncResult: Map<String, Any?>? = null,
    ) {
        requireState(status in RUNNABLE && decision != null && executionResult == null)
        val at = now()
        executionResult = linkedMapOf<String, Any?>(
            "success" to success,
            "stdout" to stdout.withoutNul(),
            "stderr" to stderr.withoutNul(),
            "exit_code" to exitCode,
            "at" to at.wire(),
        ).apply {
            mergeCommit?.let { put(MERGE_COMMIT, it.withoutNul()) }
            syncResult?.let { put(SYNC_RESULT, it.withoutNul()) }
        }
        if (success && exitCode == 0) {
            appliedAt = LocalDateTime.ofInstant(at, ZoneOffset.UTC)
            status = PlanStatus.APPLIED
        } else {
            markFailed(PlanFailureReason.EXECUTION_FAILED)
        }
    }

    fun fail(reason: PlanFailureReason) {
        requireState(status.open)
        markFailed(reason)
    }

    fun markUnknown() {
        requireState(status in RUNNABLE && executionResult == null)
        status = PlanStatus.UNKNOWN
    }

    fun close() {
        check(status.open) { "닫힌 계획은 다시 닫지 않는다: $status" }
        status = if (isApproved && executionResult == null) PlanStatus.UNKNOWN else PlanStatus.EXPIRED
    }

    private fun decide(approved: Boolean, userId: UUID) {
        decision = linkedMapOf("approved" to approved, "user_id" to userId.toString(), "at" to now().wire())
        status = if (approved) PlanStatus.APPROVED else PlanStatus.REJECTED
    }

    private fun markFailed(reason: PlanFailureReason) {
        status = PlanStatus.FAILED
        failureReason = reason
    }

    private fun mismatchedFields(call: PlanCall): List<String> = buildList {
        if (call.tool != toolName) add("tool")
        if (!JsonValues.same(call.args.withoutNul(), payload["args"])) add("args")
        if (!JsonValues.same(call.command.withoutNul(), payload["command"])) add("command")
        if (call.risk != risk) add("risk")
        if (!JsonValues.same(call.target.withoutNul(), payload["target"])) add("target")
    }

    private fun requireState(allowed: Boolean) {
        if (!allowed) throw PlanStateConflictException(status)
    }

    companion object {
        const val DRY_RUN_RESULT = "dry_run_result"
        const val DECISION_GUIDANCE = "decision_guidance"
        const val PULL_REQUEST = "pull_request"
        const val MERGE_COMMIT = "merge_commit"
        const val SYNC_RESULT = "sync_result"

        private val DECIDABLE = setOf(PlanStatus.DRAFT, PlanStatus.WAITING_APPROVAL)
        private val RUNNABLE = setOf(PlanStatus.APPROVED, PlanStatus.EXECUTING)

        private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        private fun Instant.wire(): String = DateTimeFormatter.ISO_INSTANT.format(this)
    }
}
