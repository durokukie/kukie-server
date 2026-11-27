package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatRunRepository
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.util.UUID

@Service
class GetWorklogService(
    private val chatPermission: ChatPermission,
    private val chatRunRepository: ChatRunRepository,
    private val clusterRepository: ClusterRepository,
    private val worklogAssembler: WorklogAssembler,
) {

    @Transactional(readOnly = true)
    operator fun invoke(conversationId: UUID, userId: UUID, zone: ZoneId): WorklogResponse {
        val session = chatPermission.requireReadable(conversationId, userId).session
        val cluster = clusterRepository.findByIdOrThrow(session.clusterId)
        val runs = chatRunRepository.findTurnsBySessionId(session.id)

        return worklogAssembler.assemble(session, cluster, runs, zone)
    }
}
