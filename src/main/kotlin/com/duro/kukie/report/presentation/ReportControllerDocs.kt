package com.duro.kukie.report.presentation

import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.report.exception.ReportChangedException
import com.duro.kukie.report.exception.ReportNotFoundException
import com.duro.kukie.report.exception.ReportNotReadyException
import com.duro.kukie.report.exception.TooManyReportsException
import com.duro.kukie.report.presentation.dto.request.UpdateReportRequest
import com.duro.kukie.report.presentation.dto.response.ReportResponse
import com.duro.kukie.report.presentation.dto.response.ReportSummaryResponse
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ReportControllerDocs {

    @Operation(
        summary = "대화 보고서 만들기",
        description = "대화의 턴을 작업 단위로 묶은 보고서를 만듭니다. 대화를 볼 수 있는 사람이면 만들 수 있습니다. " +
            "바로 201 과 status GENERATING 인 보고서를 돌려주고 본문은 뒤에서 채우므로, READY 가 될 때까지 보고서 조회를 다시 부릅니다. " +
            "이 대화의 보고서가 이미 있고 그 뒤로 끝난 턴이 늘지 않았으면 새로 만들지 않고 그 보고서를 200 으로 돌려줍니다(가장 최근 것, " +
            "고친 내용도 그대로). 진행 중인 턴이 있으면 늘 새로 만듭니다. 같은 내용으로 다시 만들려면 앞의 보고서를 지우고 부릅니다. " +
            "본문은 요약, 한 일, 변경 표, 남은 일로 짧게 씁니다. 한 일은 AI 가 고른 작업을 한 줄씩 적되 변경(조치 계획)은 빠지지 않고, 변경 표는 저장된 기록으로 그립니다. " +
            "AI 를 쓸 수 없으면 변경만 담습니다(aiRefined false). 공개 범위는 대화를 따릅니다(Private 대화의 보고서는 만든 사람만 봅니다). " +
            "tz(IANA 시간대, 예: Asia/Seoul)는 본문의 시각을 적는 시간대이고, 비었거나 모르는 값이면 팀의 보고서 시간대입니다. " +
            "직접 만드는 보고서는 대화·주간을 합쳐 한 사람이 한 시간에 30개까지이고, 넘으면 429 REPORT_TOO_MANY 입니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "200", description = "이미 있던 보고서")
    @ApiErrorResponses(ChatNotFoundException::class, TooManyReportsException::class)
    fun createConversationReport(conversationId: UUID, userId: UUID, tz: String?): ResponseEntity<ReportResponse>

    @Operation(
        summary = "클러스터 주간 보고서 만들기",
        description = "이 클러스터의 Shared 대화에서 있었던 일을 지금까지 모은 보고서를 만듭니다. 기간은 이 클러스터의 마지막 자동 주간 보고서가 끝난 때부터 " +
            "지금까지이고, 그런 보고서가 없거나 일주일보다 오래됐으면 최근 일주일입니다. 클러스터를 볼 수 있는 팀 구성원이면 만들 수 있고, " +
            "매번 새 보고서입니다(팀이 정한 요일·시각에 자동으로 생기는 보고서와 따로입니다). 바로 201 과 status GENERATING 인 보고서를 돌려주고 " +
            "본문은 뒤에서 채웁니다. 클러스터에 Prometheus 가 있으면 그 기간의 운영 지표(노드·API 서버 가동률, CPU·메모리 사용률, 파드 재시작)를 " +
            "같이 싣고, 없거나 읽지 못하면 그 까닭을 한 줄로 적습니다. tz 는 본문의 시각을 적는 시간대이고, 비었거나 모르는 값이면 팀의 보고서 시간대입니다. " +
            "한 시간 30개 제한(429 REPORT_TOO_MANY)은 대화 보고서와 함께 셉니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(ClusterNotFoundException::class, TooManyReportsException::class)
    fun createWeeklyReport(clusterId: UUID, userId: UUID, tz: String?): ResponseEntity<ReportResponse>

    @Operation(
        summary = "보고서 목록 조회",
        description = "볼 수 있는 보고서를 최근에 만든 순으로 조회합니다. 본문은 싣지 않습니다. clusterId 를 주면 그 클러스터의 보고서만 봅니다. " +
            "Shared 보고서는 그 팀 구성원이, Private 대화의 보고서는 만든 사람만 봅니다.",
    )
    fun getReports(userId: UUID, clusterId: UUID?): ResponseEntity<List<ReportSummaryResponse>>

    @Operation(
        summary = "보고서 조회",
        description = "보고서와 본문(Markdown)을 조회합니다. 만드는 중(GENERATING)이면 content 가 null 입니다. 볼 수 없는 보고서는 404 입니다.",
    )
    @ApiErrorResponses(ReportNotFoundException::class)
    fun getReport(reportId: UUID, userId: UUID): ResponseEntity<ReportResponse>

    @Operation(
        summary = "보고서 수정",
        description = "제목과 본문(Markdown)을 통째로 바꿉니다. 볼 수 있는 사람이면 고칠 수 있습니다. version 에는 읽어 온 값을 보내고, " +
            "그 사이 다른 저장이 있었으면 409 REPORT_CHANGED 입니다(다시 읽은 뒤 고칩니다). 저장하면 version 이 1 오릅니다. " +
            "만드는 중인 보고서는 409 REPORT_NOT_READY 입니다.",
    )
    @ApiErrorResponses(ReportNotFoundException::class, ReportNotReadyException::class, ReportChangedException::class)
    fun updateReport(reportId: UUID, userId: UUID, request: UpdateReportRequest): ResponseEntity<ReportResponse>

    @Operation(
        summary = "보고서 삭제",
        description = "보고서를 지웁니다. 만든 사람이나 팀 관리자가 지울 수 있습니다(자동으로 만든 주간 보고서는 팀 관리자만).",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(ReportNotFoundException::class, NotTeamAdminException::class)
    fun deleteReport(reportId: UUID, userId: UUID): ResponseEntity<Unit>
}
