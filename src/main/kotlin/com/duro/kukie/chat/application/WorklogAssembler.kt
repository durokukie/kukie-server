package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.RunTurn
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.user.domain.UserRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Component
class WorklogAssembler(
    private val actionPlanRepository: ActionPlanRepository,
    private val userRepository: UserRepository,
) {

    fun assemble(session: ChatSession, cluster: Cluster, runs: List<RunTurn>, zone: ZoneId): WorklogResponse {
        val plans = if (runs.isEmpty()) emptyList() else actionPlanRepository.findAllByRunIdInOrderByCreatedAtAscIdAsc(runs.map { it.id })
        val exportedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val worklog = WorklogResponse.of(session, cluster, emptyList(), runs, plans.groupBy { it.runId }, zone, exportedAt)

        val peopleIds = buildSet {
            add(session.userId)
            worklog.turns.forEach { turn ->
                turn.requestedBy?.let(::add)
                turn.changes.forEach { change -> change.decision?.by?.let(::add) }
            }
        }
        val people = userRepository.findAllById(peopleIds).map { WorklogResponse.Person(it.id, it.name) }.sortedBy { it.name }

        return worklog.copy(people = people)
    }
}
