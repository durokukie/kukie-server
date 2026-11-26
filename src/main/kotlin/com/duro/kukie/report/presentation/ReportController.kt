package com.duro.kukie.report.presentation

import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.report.application.CreateConversationReportService
import com.duro.kukie.report.application.CreateWeeklyReportService
import com.duro.kukie.report.application.DeleteReportService
import com.duro.kukie.report.application.GetReportService
import com.duro.kukie.report.application.GetReportsService
import com.duro.kukie.report.application.UpdateReportService
import com.duro.kukie.report.presentation.dto.request.UpdateReportRequest
import com.duro.kukie.report.presentation.dto.response.ReportResponse
import com.duro.kukie.report.presentation.dto.response.ReportSummaryResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
class ReportController(
    private val createConversationReportService: CreateConversationReportService,
    private val createWeeklyReportService: CreateWeeklyReportService,
    private val getReportsService: GetReportsService,
    private val getReportService: GetReportService,
    private val updateReportService: UpdateReportService,
    private val deleteReportService: DeleteReportService,
) : ReportControllerDocs {

    @PostMapping("/conversations/{conversationId}/reports")
    override fun createConversationReport(
        @PathVariable conversationId: UUID,
        @AuthUser userId: UUID,
        @RequestParam(required = false) tz: String?,
    ): ResponseEntity<ReportResponse> {
        val response = createConversationReportService(conversationId, userId, WorklogResponse.zoneOf(tz))
        val status = if (response.created) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(status).body(response.report)
    }

    @PostMapping("/clusters/{clusterId}/weekly-reports")
    override fun createWeeklyReport(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestParam(required = false) tz: String?,
    ): ResponseEntity<ReportResponse> {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createWeeklyReportService(clusterId, userId, WorklogResponse.zoneOf(tz)))
    }

    @GetMapping("/reports")
    override fun getReports(
        @AuthUser userId: UUID,
        @RequestParam(required = false) clusterId: UUID?,
    ): ResponseEntity<List<ReportSummaryResponse>> {
        return ResponseEntity.ok(getReportsService(userId, clusterId))
    }

    @GetMapping("/reports/{reportId}")
    override fun getReport(
        @PathVariable reportId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ReportResponse> {
        return ResponseEntity.ok(getReportService(reportId, userId))
    }

    @PutMapping("/reports/{reportId}")
    override fun updateReport(
        @PathVariable reportId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: UpdateReportRequest,
    ): ResponseEntity<ReportResponse> {
        return ResponseEntity.ok(updateReportService(request.toCommand(reportId, userId)))
    }

    @DeleteMapping("/reports/{reportId}")
    override fun deleteReport(
        @PathVariable reportId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        deleteReportService(reportId, userId)

        return ResponseEntity.noContent().build()
    }
}
