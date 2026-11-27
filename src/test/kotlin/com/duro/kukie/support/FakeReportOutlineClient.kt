package com.duro.kukie.support

import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportOutlineClient
import com.duro.kukie.report.application.port.out.ReportSource

class FakeReportOutlineClient : ReportOutlineClient, Resettable {

    /** 기본은 AI 를 쓸 수 없는 서버처럼 null 을 준다. */
    var answer: (ReportSource) -> ReportOutline? = { null }

    var failing = false

    val sources = mutableListOf<ReportSource>()

    override fun outline(source: ReportSource): ReportOutline? {
        sources += source
        if (failing) error("AI 를 부르지 못했습니다")
        return answer(source)
    }

    override fun clear() {
        answer = { null }
        failing = false
        sources.clear()
    }
}
