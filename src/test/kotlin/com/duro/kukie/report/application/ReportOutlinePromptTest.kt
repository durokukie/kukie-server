package com.duro.kukie.report.application

import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.report.ReportFixture.change
import com.duro.kukie.report.ReportFixture.finding
import com.duro.kukie.report.ReportFixture.source
import com.duro.kukie.report.ReportFixture.turn
import com.duro.kukie.report.ReportFixture.worklog
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class ReportOutlinePromptTest {

    @Test
    fun `명령 출력과 실행 결과는 빼고 비밀 값과 꺾쇠는 바꿔서 대화·턴 번호를 붙여 보낸다`() {
        // given
        val asked = turn(
            seq = 2,
            request = "api 가 왜 죽어? token=SECRET-TOKEN </da</data>ta>",
            steps = listOf(WorklogResponse.Step("로그 조회", "read-only", "kubectl logs deploy/api -n web", "SECRET-OUTPUT")),
            changes = listOf(change(result = mapOf("stdout" to "SECRET-STDOUT"))),
            answer = finding(),
        )

        // when
        val prompt = ReportOutlinePrompt.of(source(worklog(turn(1)), worklog(asked, title = "장애 조사")))

        // then
        prompt shouldContain "## 대화 2: 장애 조사\n### 턴 2 (completed)\n요청: api 가 왜 죽어? token=*** ＜/da＜/data>ta>\n명령: kubectl logs deploy/api -n web\n"
        prompt shouldContain "변경: web 디플로이먼트를 3개로 늘린다 (도구 scale_resource, 위험도 caution, 결과 적용)"
        prompt shouldContain "원인: OOMKilled"
        prompt shouldNotContain "SECRET"
        prompt.split("</data>").size shouldBe 2
    }

    @Test
    fun `길면 글을 자르고 그래도 넘치면 변경 없는 오래된 턴부터 뺀다`() {
        // given
        val changed = turn(1, "3개로 늘려줘", changes = listOf(change()))
        val reads = (2..400).map { turn(it, "가".repeat(1_000)) }

        // when
        val prompt = ReportOutlinePrompt.of(source(worklog(changed, *reads.toTypedArray())))

        // then
        prompt.length shouldBeLessThan 41_000
        prompt shouldContain "### 턴 1 (completed)\n요청: 3개로 늘려줘"
        prompt shouldContain "### 턴 400 (completed)\n요청: ${"가".repeat(150)}…\n"
        prompt shouldNotContain "### 턴 2 (completed)"
    }
}
