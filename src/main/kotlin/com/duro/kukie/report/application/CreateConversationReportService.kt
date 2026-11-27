package com.duro.kukie.report.application

import com.duro.kukie.chat.application.ChatPermission
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.RunStatus
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.presentation.dto.response.CreateReportResponse
import com.duro.kukie.report.presentation.dto.response.ReportResponse
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class CreateConversationReportService(
    private val chatPermission: ChatPermission,
    private val reportQuota: ReportQuota,
    private val teamRepository: TeamRepository,
    private val chatRunRepository: ChatRunRepository,
    private val reportRepository: ReportRepository,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    operator fun invoke(conversationId: UUID, userId: UUID, zone: ZoneId?): CreateReportResponse {
        val access = chatPermission.requireReadable(conversationId, userId)
        upToDate(conversationId, userId)?.let { return CreateReportResponse(ReportResponse.of(it), created = false) }

        reportQuota.consume(userId)
        val reportZone = zone ?: teamRepository.findByIdOrThrow(access.teamId).reportZoneId
        val report = reportRepository.save(Report.ofConversation(access.session, userId, reportZone))
        events.publishEvent(ReportRequested(report.id))

        return CreateReportResponse(ReportResponse.of(report), created = true)
    }

    /** 다시 만들어도 같은 내용일 보고서. 진행 중인 턴이 있으면 내용이 아직 바뀌므로 없다고 본다. */
    private fun upToDate(conversationId: UUID, userId: UUID): Report? {
        if (chatRunRepository.existsBySessionIdAndStatusIn(conversationId, RunStatus.ACTIVE)) return null

        val lastTurn = chatRunRepository.findMaxTurnNo(conversationId) ?: 0
        // created_at 은 JPA 감사가 시스템 시간대로 적는다
        val generatingSince = LocalDateTime.now().minus(ResumeStalledReportsService.STALLED_AFTER)

        return reportRepository.findUpToDateOfConversation(conversationId, userId, lastTurn, generatingSince).firstOrNull()
    }
}
