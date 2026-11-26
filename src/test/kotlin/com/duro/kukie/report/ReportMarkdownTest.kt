package com.duro.kukie.report

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.report.ReportFixture.change
import com.duro.kukie.report.ReportFixture.finding
import com.duro.kukie.report.ReportFixture.source
import com.duro.kukie.report.ReportFixture.turn
import com.duro.kukie.report.ReportFixture.worklog
import com.duro.kukie.report.application.port.out.ClusterMetrics
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportSource
import com.duro.kukie.report.domain.ReportKind
import com.duro.kukie.report.presentation.ReportMarkdown
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

class ReportMarkdownTest {

    private val conversationId = UUID.randomUUID()

    @Test
    fun `AI 가 고른 작업만 한 줄씩 담고 턴의 문답은 옮기지 않는다`() {
        // given
        val source = source(worklog(turn(1, "안녕"), turn(2), turn(3, "api 가 왜 죽어?", answer = finding()), id = conversationId))
        val outline = outline(task("api 재시작 원인 조사", 3, summary = "메모리 한도 초과로 OOMKilled 반복.", followUps = listOf("메모리 한도 올리기")))

        // when
        val markdown = render(source, outline)

        // then
        markdown shouldContain "# 보고서\n\n운영 · namespace `web` · 2026-11-04 (UTC) · 변경 없음\n\n## 요약\n\n전체 요약입니다.\n"
        markdown shouldContain "## 한 일\n\n- **api 재시작 원인 조사**: 메모리 한도 초과로 OOMKilled 반복.\n"
        markdown shouldContain "## 변경\n\n없음\n"
        markdown shouldContain "## 남은 일\n\n- 메모리 한도 올리기\n"
        markdown shouldNotContain "안녕"
        markdown shouldNotContain "api 가 왜 죽어?"
        markdown shouldNotContain "exit 137"
    }

    @Test
    fun `변경이 있는 턴은 AI 가 빼도 한 일에 넣는다`() {
        // given
        val source = source(worklog(turn(1, "3개로 늘려줘", changes = listOf(change())), turn(2), id = conversationId))
        val outline = outline(task("파드 조회", 2))

        // when
        val markdown = render(source, outline)

        // then
        markdown shouldContain "## 한 일\n\n- **파드 조회**\n- web 디플로이먼트를 3개로 늘린다 (적용)\n"
    }

    @Test
    fun `주간 보고서는 변경을 요청한 사람별로 묶고 누가 결정했는지 적는다`() {
        // given
        val kim = UUID.randomUUID()
        val lee = UUID.randomUUID()
        val people = mapOf(kim to "김", lee to "이")
        val source = source(
            worklog(
                turn(1, requestedBy = kim, changes = listOf(change(decidedBy = lee), change("api 를 재시작한다", PlanStatus.FAILED, decidedBy = lee))),
                title = "배포",
                people = people,
            ),
            worklog(
                turn(1, requestedBy = lee, changes = listOf(change("web 을 지운다", PlanStatus.REJECTED, approved = false, decidedBy = kim))),
                title = "정리",
                people = people,
            ),
            kind = ReportKind.WEEKLY,
        )

        // when
        val markdown = render(source, outline = null)

        // then
        markdown shouldContain "운영 · 2026-11-02 00:00 ~ 2026-11-09 00:00 (UTC) · 대화 2개 · 변경 3건 (적용 1 · 실패 1 · 거절 1)\n"
        markdown shouldContain "## 주요 작업\n\n- web 디플로이먼트를 3개로 늘린다 (적용 · 김, 11-04)\n- api 를 재시작한다 (실패 · 김, 11-04)\n- web 을 지운다 (거절 · 이, 11-04)\n"
        markdown shouldContain "### 김 · 2건\n\n| 시각 | 대화 | 대상 | 위험도 | 결정 | 결과 | PR |"
        markdown shouldContain "| 11-04 12:49 | 배포 | scale `deployment/web` | caution | 이 승인 | 적용 | - |"
        markdown shouldContain "### 이 · 1건"
        markdown shouldContain "| 11-04 12:49 | 정리 | scale `deployment/web` | caution | 김 거절 | 거절 | - |"
        markdown shouldContain "## 남은 일\n\n- 실패: api 를 재시작한다 (배포)\n"
    }

    @Test
    fun `주간 보고서에 기간 중 운영 지표를 표로 싣고 못 읽은 항목은 뺀다`() {
        // given
        val source = source(worklog(turn(1, changes = listOf(change()))), kind = ReportKind.WEEKLY)
        val metrics = ClusterMetrics(
            nodes = 3,
            nodeReady = 0.99999,
            apiServerUp = 1.0,
            workloadsAvailable = null,
            cpuAverage = 0.342,
            cpuPeak = 0.708,
            memoryAverage = null,
            memoryPeak = null,
            restarts = 12,
            topRestarts = listOf(ClusterMetrics.PodRestarts("web", "api-7d9f", 7)),
        )

        // when
        val markdown = ReportMarkdown.render("보고서", ZoneId.of("UTC"), source, null, ClusterMetricsResult.Available(metrics))

        // then
        markdown shouldContain """
            |## 운영 지표
            |
            || 항목 | 기간 중 값 |
            ||---|---|
            || 노드 | 3개 · Ready 99.99% |
            || API 서버 가동률 | 100% |
            || CPU 사용률 | 평균 34% · 최대 71% |
            || 파드 재시작 | 12회 (`web/api-7d9f` 7회) |
            |
            |## 주요 작업
        """.trimMargin()
    }

    @Test
    fun `운영 지표를 읽지 못했으면 그 까닭을 한 줄로 적는다`() {
        // given
        val source = source(worklog(turn(1)), kind = ReportKind.WEEKLY)

        // when
        val none = ReportMarkdown.render("보고서", ZoneId.of("UTC"), source, null, ClusterMetricsResult.NoPrometheus)
        val unreadable = ReportMarkdown.render("보고서", ZoneId.of("UTC"), source, null, ClusterMetricsResult.Unreadable)

        // then
        none shouldContain "## 운영 지표\n\n클러스터에서 Prometheus 를 찾지 못해 지표를 싣지 못함.\n\n변경이나 기록할 작업 없음.\n"
        unreadable shouldContain "## 운영 지표\n\nPrometheus 를 읽지 못함."
    }

    @Test
    fun `AI 정리가 없으면 변경만 담는다`() {
        // given
        val source = source(
            worklog(
                turn(1),
                turn(2, "3개로 늘려줘", changes = listOf(change())),
                turn(3, "api 가 왜 죽어?", answer = finding(suggestedNextAction = "메모리 한도를 올리세요")),
                turn(4, kind = RunKind.MODE_CHANGE),
                id = conversationId,
            ),
        )

        // when
        val markdown = render(source, outline = null)

        // then
        markdown shouldContain "운영 · namespace `web` · 2026-11-04 (UTC) · 변경 1건 (적용 1)\n\n## 한 일\n\n- web 디플로이먼트를 3개로 늘린다 (적용)\n"
        markdown shouldContain "## 변경\n\n| 시각 | 요청 | 대상 | 위험도 | 결정 | 결과 | PR |\n|---|---|---|---|---|---|---|\n| 11-04 12:49 | - | scale `deployment/web` | caution | - | 적용 | - |\n"
        markdown shouldContain "## 남은 일\n\n없음\n"
        markdown shouldNotContain "OOMKilled"
        markdown shouldNotContain "메모리 한도를 올리세요"
    }

    @Test
    fun `변경도 고른 작업도 없으면 그렇게만 적는다`() {
        // given
        val source = source(worklog(turn(1), turn(2, "api 가 왜 죽어?", answer = finding()), id = conversationId))

        // when
        val markdown = render(source, outline = null)

        // then
        markdown shouldContain "· 변경 없음\n\n변경이나 기록할 작업 없음.\n"
        markdown shouldNotContain "## "
    }

    @Test
    fun `여럿이 쓴 대화는 누가 한 일인지 붙인다`() {
        // given
        val kim = UUID.randomUUID()
        val lee = UUID.randomUUID()
        val source = source(
            worklog(
                turn(1, requestedBy = kim, changes = listOf(change())),
                turn(2, requestedBy = lee),
                id = conversationId,
                people = mapOf(kim to "김", lee to "이"),
            ),
        )

        // when
        val markdown = render(source, outline(task("파드 조회", 2)))

        // then
        markdown shouldContain "· 김, 이 · 변경 1건 (적용 1)\n"
        markdown shouldContain "- **파드 조회** (이)\n- web 디플로이먼트를 3개로 늘린다 (적용 · 김)\n"
        markdown shouldContain "| 11-04 12:49 | 김 | scale `deployment/web` |"
    }

    @Test
    fun `자료에 없는 턴을 가리키는 작업은 버린다`() {
        // given
        val source = source(worklog(turn(1), id = conversationId))
        val outline = ReportOutline(
            summary = null,
            tasks = listOf(
                ReportOutline.Task("없는 턴", null, listOf(ReportOutline.TurnRef(conversationId, 99)), emptyList()),
                ReportOutline.Task("없는 대화", null, listOf(ReportOutline.TurnRef(UUID.randomUUID(), 1)), emptyList()),
                ReportOutline.Task("파드 조회", null, listOf(ReportOutline.TurnRef(conversationId, 1), ReportOutline.TurnRef(conversationId, 1)), emptyList()),
            ),
        )

        // when
        val markdown = render(source, outline)

        // then
        markdown shouldContain "## 한 일\n\n- **파드 조회**\n"
        markdown shouldNotContain "없는 턴"
        markdown shouldNotContain "없는 대화"
    }

    @Test
    fun `사람과 AI 가 쓴 글의 링크·이미지·HTML 문법은 글자로만 남긴다`() {
        // given
        val failed = change("<img src=x> 를 [지운다](https://evil.example)", PlanStatus.FAILED, target = mapOf("kind" to "pod", "name" to "a`|b"))
        val source = source(worklog(turn(1, "![x](https://evil.example/a) 해줘", changes = listOf(failed)), id = conversationId))
        val outline = ReportOutline("요약 ![p](https://evil.example/?q=1)", listOf(ReportOutline.Task("[작업](https://evil.example)", "줄1\n## 줄2", listOf(ReportOutline.TurnRef(conversationId, 1)), emptyList())))

        // when
        val markdown = ReportMarkdown.render("[제목](https://evil.example)", ZoneId.of("UTC"), source, outline)

        // then
        markdown shouldContain "# \\[제목\\](https://evil.example)\n"
        markdown shouldContain "요약 !\\[p\\](https://evil.example/?q=1)"
        markdown shouldContain "- **\\[작업\\](https://evil.example)**: 줄1 ## 줄2\n"
        markdown shouldContain "- 실패: \\<img src=x> 를 \\[지운다\\](https://evil.example)\n"
        markdown shouldContain "| scale ``pod/a`\\|b`` |"
        markdown shouldNotContain Regex("""(?<!\\)\]\(""")
    }

    @Test
    fun `줄 머리에 오는 글이 코드 블록·제목·목록으로 읽히지 않게 한다`() {
        // given
        val source = source(worklog(turn(1, changes = listOf(change("2026. 11. 17 점검용 파드를 띄운다"))), turn(2), id = conversationId))
        val outline = ReportOutline("~~~ 이번 주 요약", listOf(task("파드 조회", 2, followUps = listOf("# 제목 아님"))))

        // when
        val markdown = render(source, outline)

        // then
        markdown shouldContain "## 요약\n\n\\~~~ 이번 주 요약\n"
        markdown shouldContain "- 2026\\. 11. 17 점검용 파드를 띄운다 (적용)\n"
        markdown shouldContain "- \\# 제목 아님\n"
    }

    private fun render(source: ReportSource, outline: ReportOutline?): String =
        ReportMarkdown.render("보고서", ZoneId.of("UTC"), source, outline)

    private fun outline(vararg tasks: ReportOutline.Task) = ReportOutline("전체 요약입니다.", tasks.toList())

    private fun task(title: String, seq: Int, summary: String? = null, followUps: List<String> = emptyList()) =
        ReportOutline.Task(title, summary, listOf(ReportOutline.TurnRef(conversationId, seq)), followUps)
}
