package com.duro.kukie.report.application

import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.report.domain.Report
import com.duro.kukie.report.domain.ReportRepository
import com.duro.kukie.report.domain.findByIdOrThrow
import com.duro.kukie.report.exception.ReportNotFoundException
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.NotTeamAdminException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReportPermission(
    private val reportRepository: ReportRepository,
    private val clusterRepository: ClusterRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    fun requireReadable(reportId: UUID, userId: UUID): Report =
        requireReadable(reportRepository.findByIdOrThrow(reportId), userId)

    /** ReportRepository.findAllVisibleTo 의 조건과 같아야 한다. */
    fun requireReadable(report: Report, userId: UUID): Report {
        val readable = if (report.shared) {
            teamMembershipRepository.existsByTeamIdAndUserId(teamOf(report), userId)
        } else {
            report.ownerId == userId
        }
        if (!readable) throw ReportNotFoundException()

        return report
    }

    fun requireDeletable(report: Report, userId: UUID) {
        if (report.ownerId == userId) return

        val membership = teamMembershipRepository.findByTeamIdAndUserId(teamOf(report), userId)
        if (membership?.role != TeamRole.ADMIN) throw NotTeamAdminException(DELETER_REQUIRED)
    }

    private fun teamOf(report: Report): UUID = clusterRepository.findByIdOrThrow(report.clusterId).teamId

    companion object {
        private const val DELETER_REQUIRED = "보고서는 만든 사람이나 팀 관리자만 지울 수 있습니다."
    }
}
