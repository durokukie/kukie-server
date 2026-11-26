package com.duro.kukie.report

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.report.application.port.out.ReportSource
import com.duro.kukie.report.domain.ReportKind
import java.time.Instant
import java.util.UUID

object ReportFixture {

    val STARTED: Instant = Instant.parse("2026-11-04T12:49:00Z")

    fun source(vararg conversations: WorklogResponse, kind: ReportKind = ReportKind.CONVERSATION) = ReportSource(
        kind = kind,
        clusterName = "운영",
        periodStart = if (kind == ReportKind.WEEKLY) Instant.parse("2026-11-02T00:00:00Z") else null,
        periodEnd = if (kind == ReportKind.WEEKLY) Instant.parse("2026-11-09T00:00:00Z") else null,
        conversations = conversations.toList(),
    )

    fun worklog(
        vararg turns: WorklogResponse.Turn,
        id: UUID = UUID.randomUUID(),
        title: String = "api 점검",
        people: Map<UUID, String> = emptyMap(),
    ) = WorklogResponse(
        schema = WorklogResponse.SCHEMA,
        exportedAt = STARTED,
        timeZone = "UTC",
        utcOffset = "+00:00",
        conversation = WorklogResponse.Conversation(id, title, true, UUID.randomUUID(), STARTED),
        environment = WorklogResponse.Environment(UUID.randomUUID(), "운영", "prod", "web", "fp"),
        people = people.map { WorklogResponse.Person(it.key, it.value) },
        turns = turns.toList(),
    )

    fun turn(
        seq: Int,
        request: String = "파드 목록 보여줘",
        requestedBy: UUID? = null,
        steps: List<WorklogResponse.Step> = emptyList(),
        changes: List<WorklogResponse.Change> = emptyList(),
        answer: WorklogResponse.Answer? = null,
        kind: RunKind = RunKind.CHAT,
    ) = WorklogResponse.Turn(
        seq = seq,
        kind = kind,
        mode = "학습",
        status = RunStatus.COMPLETED,
        requestedBy = requestedBy,
        request = request,
        startedAt = STARTED.plusSeconds(seq * 60L),
        finishedAt = STARTED.plusSeconds(seq * 60L + 10),
        steps = steps,
        changes = changes,
        answer = answer,
        error = null,
    )

    fun change(
        intent: String = "web 디플로이먼트를 3개로 늘린다",
        status: PlanStatus = PlanStatus.APPLIED,
        approved: Boolean = true,
        decidedBy: UUID? = null,
        result: Map<String, Any?>? = null,
        target: Any? = mapOf("namespace" to "web", "resources" to listOf(mapOf("kind" to "deployment", "name" to "web"))),
    ) = WorklogResponse.Change(
        code = "ap-1",
        tool = "scale_resource",
        intent = intent,
        args = null,
        command = emptyList(),
        target = target,
        risk = "caution",
        status = status,
        failureReason = null,
        dryRun = null,
        decision = decidedBy?.let { WorklogResponse.Decision(approved, it, STARTED.plusSeconds(30)) },
        result = result,
        pullRequest = null,
        appliedAt = null,
        proposedAt = STARTED,
    )

    fun finding(cause: String = "OOMKilled", suggestedNextAction: String? = null) = WorklogResponse.Answer(
        narration = "메모리가 부족해요",
        finding = WorklogResponse.Finding(cause, "exit 137", "한도를 올리세요"),
        suggestedNextAction = suggestedNextAction,
    )
}
