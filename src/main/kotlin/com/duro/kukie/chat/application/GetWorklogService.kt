package com.duro.kukie.chat.application

import com.duro.kukie.actionplan.domain.ActionPlanRepository
import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class GetWorklogService(
    private val chatPermission: ChatPermission,
    private val chatRunRepository: ChatRunRepository,
    private val actionPlanRepository: ActionPlanRepository,
    private val clusterRepository: ClusterRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(conversationId: UUID, userId: UUID, zone: ZoneId): WorklogResponse {
        val session = chatPermission.requireReadable(conversationId, userId).session
        val cluster = clusterRepository.findByIdOrThrow(session.clusterId)
        val runs = chatRunRepository.findTurnsBySessionId(session.id)
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
