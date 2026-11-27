package com.duro.kukie.report.application.port.out

interface ReportOutlineClient {

    /** AI 를 쓸 수 없게 설정됐으면 null. 호출이 실패하면 예외를 던진다. */
    fun outline(source: ReportSource): ReportOutline?
}
