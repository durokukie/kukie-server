package com.duro.kukie.report.application

import com.duro.kukie.chat.application.WorklogAssembler
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.global.util.logger
import com.duro.kukie.notification.domain.Notification
import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.report.application.port.out.ClusterMetricsReader
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportOutlineClient
import com.duro.kukie.report.application.port.out.ReportSource
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportKind
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.presentation.ReportMarkdown
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Service
class GenerateReportService(
    private val reportRepository: ReportRepository,
    private val clusterRepository: ClusterRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatRunRepository: ChatRunRepository,
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
    private val notificationRepository: NotificationRepository,
    private val worklogAssembler: WorklogAssembler,
    private val reportOutlineClient: ReportOutlineClient,
    private val clusterMetricsReader: ClusterMetricsReader,
    transactionManager: PlatformTransactionManager,
) {

    private val log = logger()

    // 커밋 직후의 스레드에서 불려도 끝난 트랜잭션에 끼지 않게 늘 새로 연다
    private val reading = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }
    private val writing = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    private sealed interface Material {
        /** cluster 는 주간 보고서에만 있다 — 그 클러스터의 운영 지표를 읽는다. */
        data class Ready(val source: ReportSource, val throughTurn: Int?, val cluster: Cluster? = null) : Material

        data object Orphaned : Material
    }

    /** AI 와 클러스터를 기다리는 동안 DB 연결을 쥐지 않는다. */
    operator fun invoke(reportId: UUID) {
        when (val material = reading.execute { material(reportId) }) {
            null -> return
            Material.Orphaned -> writing.executeWithoutResult { discard(reportId) }
            is Material.Ready -> {
                val outline = outline(reportId, material.source)
                val metrics = metrics(material)
                writing.executeWithoutResult { complete(reportId, material, outline, metrics) }
            }
        }
    }

    private fun material(reportId: UUID): Material? {
        val report = reportRepository.findByIdOrNull(reportId)?.takeIf { it.isGenerating } ?: return null
        val cluster = clusterRepository.findByIdOrThrow(report.clusterId)
        val zone = ZoneId.of(report.timeZone)

        if (report.kind == ReportKind.CONVERSATION) {
            val session = report.conversationId?.let(chatSessionRepository::findByIdOrNull) ?: return Material.Orphaned
            val runs = chatRunRepository.findTurnsBySessionId(session.id)
            val worklog = worklogAssembler.assemble(session, cluster, runs, zone)

            // 진행 중인 턴은 세지 않는다 — 그 턴이 끝난 뒤 다시 만들면 새 보고서가 돼야 한다
            val throughTurn = runs.filterNot { it.status.active }.maxOfOrNull { it.turnNo }

            return Material.Ready(ReportSource(report.kind, cluster.name, null, null, listOf(worklog)), throughTurn)
        }

        val from = checkNotNull(report.periodStart)
        val to = checkNotNull(report.periodEnd)
        val sessionIds = chatRunRepository.findSharedSessionIdsActiveBetween(cluster.id, from, to)
        val worklogs = chatSessionRepository.findAllById(sessionIds).sortedBy { it.createdAt }.map { session ->
            worklogAssembler.assemble(session, cluster, chatRunRepository.findTurnsBySessionIdBetween(session.id, from, to), zone)
        }

        return Material.Ready(
            ReportSource(report.kind, cluster.name, from.toInstant(ZoneOffset.UTC), to.toInstant(ZoneOffset.UTC), worklogs),
            throughTurn = null,
            cluster = cluster,
        )
    }

    private fun metrics(material: Material.Ready): ClusterMetricsResult? {
        val cluster = material.cluster ?: return null
        val from = material.source.periodStart ?: return null
        val to = material.source.periodEnd ?: return null

        return clusterMetricsReader.read(cluster, from, to)
    }

    private fun outline(reportId: UUID, source: ReportSource): ReportOutline? {
        if (source.conversations.none { worklog -> worklog.turns.any { it.kind == RunKind.CHAT } }) return null

        return try {
            reportOutlineClient.outline(source)
        } catch (exception: Exception) {
            log.warn("보고서를 AI 로 정리하지 못해 기본형으로 만듭니다: report={} reason={}", reportId, exception.toString())
            null
        }
    }

    private fun complete(reportId: UUID, material: Material.Ready, outline: ReportOutline?, metrics: ClusterMetricsResult?) {
        val report = reportRepository.lockById(reportId)?.takeIf { it.isGenerating } ?: return
        val content = ReportMarkdown.render(report.title, ZoneId.of(report.timeZone), material.source, outline, metrics)

        report.complete(content, aiRefined = outline != null, throughTurn = material.throughTurn)
        if (report.isScheduled) notifyTeam(report)
    }

    private fun discard(reportId: UUID) {
        reportRepository.lockById(reportId)?.takeIf { it.isGenerating }?.let(reportRepository::delete)
    }

    private fun notifyTeam(report: Report) {
        val team = teamRepository.findByIdOrThrow(clusterRepository.findByIdOrThrow(report.clusterId).teamId)
        val notifications = teamMembershipRepository.findAllByTeamId(team.id).map {
            Notification.weeklyReportReady(it.userId, team.id, team.name, report.id, report.title)
        }

        notificationRepository.saveAll(notifications)
    }
}
