package com.duro.kukie.team.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.team.application.port.`in`.UpdateReportScheduleCommand
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRepository
import com.duro.kukie.team.domain.findByIdOrThrow
import com.duro.kukie.team.domain.findByTeamIdAndUserIdOrThrow
import com.duro.kukie.team.exception.InvalidReportZoneException
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.ZoneId

@Service
class UpdateReportScheduleService(
    private val teamRepository: TeamRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateReportScheduleCommand): TeamResponse {
        val zone = try {
            ZoneId.of(command.timeZone.trim())
        } catch (exception: DateTimeException) {
            throw InvalidReportZoneException()
        }
        val team = teamRepository.findByIdOrThrow(command.teamId)
        team.changeReportSchedule(command.enabled, command.day, command.time, zone, utcNow())

        val membership = teamMembershipRepository.findByTeamIdAndUserIdOrThrow(command.teamId, command.userId)

        return TeamResponse.of(team, membership.role)
    }
}
