package com.duro.kukie.chat

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.chat.presentation.WorklogMarkdown
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class WorklogMarkdownTest {

    private val owner = UUID.randomUUID()
    private val started = Instant.parse("2026-11-04T12:49:19.153Z")

    @Test
    fun `시각을 받은 시간대로 적고 시간대와 오프셋을 붙인다`() {
        val markdown = WorklogMarkdown.render(worklog(ZoneId.of("Asia/Seoul"), turn(finishedAt = started.plusSeconds(18))))

        markdown shouldContain "- 대화를 만든 시각: 2026-11-04 21:49:00 (Asia/Seoul, UTC+09:00)"
        markdown shouldContain "- 내보낸 시각: 2026-11-06 08:00:00 (Asia/Seoul, UTC+09:00)"
        markdown shouldContain "- 시작: 2026-11-04 21:49:19 (Asia/Seoul, UTC+09:00)"
        markdown shouldContain "- 끝: 2026-11-04 21:49:37 (Asia/Seoul, UTC+09:00)"
        markdown shouldContain "- 소요: 18초"
    }

    @Test
    fun `서머타임 시간대는 시각마다 오프셋이 다르다`() {
        val summer = Instant.parse("2026-07-01T00:00:00Z")
        val markdown = WorklogMarkdown.render(
            worklog(ZoneId.of("America/New_York"), turn(startedAt = summer, finishedAt = summer.plusSeconds(3725))),
        )

        markdown shouldContain "- 시작: 2026-06-30 20:00:00 (America/New_York, UTC-04:00)"
        markdown shouldContain "- 내보낸 시각: 2026-11-05 18:00:00 (America/New_York, UTC-05:00)"
        markdown shouldContain "- 소요: 1시간 2분"
    }

    @Test
    fun `끝나지 않은 턴은 진행 중으로 적는다`() {
        val markdown = WorklogMarkdown.render(worklog(ZoneId.of("UTC"), turn(finishedAt = null, status = RunStatus.RUNNING)))

        markdown shouldContain "- 끝: 진행 중"
        markdown shouldContain "- 소요: 진행 중"
    }

    @Test
    fun `변경의 결정 시각과 대기 시간, 적용 시각을 적는다`() {
        val proposed = started.plusSeconds(5)
        val change = change(proposedAt = proposed, decidedAt = proposed.plusSeconds(125), appliedAt = proposed.plusSeconds(130))
        val markdown = WorklogMarkdown.render(worklog(ZoneId.of("UTC"), turn(finishedAt = started.plusSeconds(200), changes = listOf(change))))

        markdown shouldContain "- 결정: 승인 · Bok · 2026-11-04 12:51:29 (UTC, UTC+00:00) · 제안 후 2분 5초 만에 결정"
        markdown shouldContain "- 적용 시각: 2026-11-04 12:51:34 (UTC, UTC+00:00)"
    }

    @Test
    fun `결정·적용 시각이 없으면 그 줄을 비운다`() {
        val change = change(proposedAt = started, decidedAt = null, appliedAt = null)
        val markdown = WorklogMarkdown.render(worklog(ZoneId.of("UTC"), turn(finishedAt = started, changes = listOf(change))))

        markdown shouldContain "- 소요: 1초 미만"
        markdown shouldNotContain "- 결정:"
        markdown shouldNotContain "- 적용 시각:"
    }

    @Test
    fun `모르는 시간대는 UTC 로 본다`() {
        WorklogResponse.zoneOf("Mars/Olympus") shouldBe ZoneId.of("UTC")
        WorklogResponse.zoneOf(" ") shouldBe ZoneId.of("UTC")
        WorklogResponse.zoneOf(null) shouldBe ZoneId.of("UTC")
        WorklogResponse.zoneOf("Asia/Seoul") shouldBe ZoneId.of("Asia/Seoul")
    }

    private fun worklog(zone: ZoneId, vararg turns: WorklogResponse.Turn): WorklogResponse {
        val exportedAt = Instant.parse("2026-11-05T23:00:00Z")
        return WorklogResponse(
            schema = WorklogResponse.SCHEMA,
            exportedAt = exportedAt,
            timeZone = zone.id,
            utcOffset = WorklogResponse.offsetOf(zone, exportedAt),
            conversation = WorklogResponse.Conversation(UUID.randomUUID(), "파드", false, owner, Instant.parse("2026-11-04T12:49:00Z")),
            environment = WorklogResponse.Environment(UUID.randomUUID(), "dev", "prod", "web", "fp"),
            people = listOf(WorklogResponse.Person(owner, "Bok")),
            turns = turns.toList(),
        )
    }

    private fun turn(
        startedAt: Instant = started,
        finishedAt: Instant?,
        status: RunStatus = RunStatus.COMPLETED,
        changes: List<WorklogResponse.Change> = emptyList(),
    ) = WorklogResponse.Turn(
        seq = 1,
        kind = RunKind.CHAT,
        mode = "학습",
        status = status,
        requestedBy = owner,
        request = "파드 목록",
        startedAt = startedAt,
        finishedAt = finishedAt,
        steps = emptyList(),
        changes = changes,
        answer = null,
        error = null,
    )

    private fun change(proposedAt: Instant, decidedAt: Instant?, appliedAt: Instant?) = WorklogResponse.Change(
        code = "P-1",
        tool = "scale",
        intent = "늘리기",
        args = null,
        command = emptyList(),
        target = null,
        risk = "caution",
        status = PlanStatus.APPLIED,
        failureReason = null,
        dryRun = null,
        decision = decidedAt?.let { WorklogResponse.Decision(true, owner, it) },
        result = null,
        pullRequest = null,
        appliedAt = appliedAt,
        proposedAt = proposedAt,
    )
}
