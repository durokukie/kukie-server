package com.duro.kukie.chat.application

import com.duro.kukie.chat.application.port.`in`.CreateChatCommand
import com.duro.kukie.chat.domain.ChatSession
import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.chat.exception.ChatNamespaceNotAllowedException
import com.duro.kukie.chat.exception.ClusterRequiredException
import com.duro.kukie.chat.exception.ClusterTeamMismatchException
import com.duro.kukie.chat.presentation.dto.response.ChatResponse
import com.duro.kukie.chat.presentation.dto.response.CreateChatResponse
import com.duro.kukie.chat.presentation.dto.response.SessionResponse
import com.duro.kukie.cluster.application.ClusterPermission
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.NamespaceName
import com.duro.kukie.cluster.exception.InvalidNamespaceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateChatService(
    private val chatSessionRepository: ChatSessionRepository,
    private val clusterPermission: ClusterPermission,
) {

    @Transactional
    operator fun invoke(command: CreateChatCommand): CreateChatResponse {
        val clusterId = command.clusterId ?: throw ClusterRequiredException()
        val cluster = clusterPermission.requireReadable(clusterId, command.userId)
        if (command.teamId != null && command.teamId != cluster.teamId) throw ClusterTeamMismatchException()

        val session = chatSessionRepository.save(
            ChatSession(
                userId = command.userId,
                clusterId = cluster.id,
                title = command.title,
                contextName = cluster.contextName,
                namespace = namespaceOf(cluster, command.namespace),
                clusterFingerprint = cluster.fingerprint,
                visibility = command.visibility,
            ),
        )

        return CreateChatResponse(
            conversation = ChatResponse.of(session, cluster.teamId, running = false),
            session = SessionResponse.of(session, pending = emptyList()),
        )
    }

    /**
     * 대화가 쓸 namespace (DURO-228). 비우면 등록한 namespace 다. limited 로 연결한 EKS 는 Kukie 권한이 등록한 namespace 에만 있어
     * 그 하나만 받는다. full·kubeconfig 클러스터는 이름 꼴만 본다 — 있는지는 화면이 목록으로 고르게 하고, 없으면 kubectl 이 알린다.
     */
    private fun namespaceOf(cluster: Cluster, requested: String?): String {
        if (requested == null || requested == cluster.defaultNamespace) return cluster.defaultNamespace
        if (!NamespaceName.isAllowed(requested)) throw InvalidNamespaceException()
        if (cluster.provider == ClusterProvider.EKS && cluster.accessLevel == AccessLevel.LIMITED) {
            throw ChatNamespaceNotAllowedException()
        }
        return requested
    }
}
