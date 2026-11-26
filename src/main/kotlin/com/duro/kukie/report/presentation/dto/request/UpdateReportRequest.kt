package com.duro.kukie.report.presentation.dto.request

import com.duro.kukie.report.application.port.`in`.UpdateReportCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateReportRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:Size(max = CONTENT_LIMIT)
    val content: String,

    /** 읽어 온 보고서의 version. 그 사이 다른 저장이 있었으면 409 */
    val version: Int,
) {
    fun toCommand(reportId: UUID, userId: UUID) = UpdateReportCommand(
        reportId = reportId,
        userId = userId,
        title = title,
        content = content,
        version = version,
    )

    companion object {
        const val CONTENT_LIMIT = 500_000
    }
}
