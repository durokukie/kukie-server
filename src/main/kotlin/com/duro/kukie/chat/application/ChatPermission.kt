package com.duro.kukie.chat.application

import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.domain.ChatVisibility
import com.duro.kukie.chat.domain.findByIdOrThrow
import com.duro.kukie.chat.exception.ChatNotFoundException
import com.duro.kukie.chat.exception.PrivateSessionException
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByIdOrThrow
import com.duro.kukie.team.domain.TeamMembershipRepository
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.NotTeamAdminException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatPermission(
    private val chatSessionRepository: ChatSessionRepository,
    private val clusterRepository: ClusterRepository,
    private val teamMembershipRepository: TeamMembershipRepository,
) {

    fun requireReadable(conversationId: UUID, userId: UUID): ChatAccess =
        requireReadable(chatSessionRepository.findByIdOrThrow(conversationId), userId)

    fun requireReadable(session: ChatSession, userId: UUID): ChatAccess {
        val teamId = teamOf(session)
        if (!canRead(session, teamId, userId)) throw ChatNotFoundException()

        return ChatAccess(session, teamId)
    }

    fun isReadable(session: ChatSession, userId: UUID): Boolean = canRead(session, teamOf(session), userId)

    /** 팀에서 나간 방 주인이 계속 승인하지 못하게 주인 여부보다 소속을 먼저 본다. */
    fun requireApprover(access: ChatAccess, userId: UUID) {
        val session = access.session
        if (session.visibility == ChatVisibility.PRIVATE) throw PrivateSessionException()

        val membership = teamMembershipRepository.findByTeamIdAndUserId(access.teamId, userId)
        if (membership == null || (membership.role != TeamRole.ADMIN && session.userId != userId)) {
            throw NotTeamAdminException(APPROVER_REQUIRED)
        }
    }

    /** private 대화는 만든 사람이, shared 대화는 팀 관리자가 지운다. */
    fun requireDeletable(access: ChatAccess, userId: UUID) {
        val session = access.session
        if (session.visibility == ChatVisibility.PRIVATE) return

        val membership = teamMembershipRepository.findByTeamIdAndUserId(access.teamId, userId)
        if (membership?.role != TeamRole.ADMIN) throw NotTeamAdminException(DELETER_REQUIRED)
    }

    private fun canRead(session: ChatSession, teamId: UUID, userId: UUID): Boolean = when (session.visibility) {
        ChatVisibility.PRIVATE -> session.userId == userId
        ChatVisibility.SHARED -> teamMembershipRepository.existsByTeamIdAndUserId(teamId, userId)
    }

    private fun teamOf(session: ChatSession): UUID = clusterRepository.findByIdOrThrow(session.clusterId).teamId

    companion object {
        private const val DELETER_REQUIRED = "공유 대화는 팀 관리자만 지울 수 있습니다."
        private const val APPROVER_REQUIRED = "승인·재개는 팀 관리자나 대화를 만든 사람만 할 수 있습니다."
    }
}
