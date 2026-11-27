package com.duro.kukie.report.presentation.dto.response

data class CreateReportResponse(
    val report: ReportResponse,
    /** false 면 새로 만들지 않고 이미 있던 보고서를 돌려준 것이다 */
    val created: Boolean,
)
