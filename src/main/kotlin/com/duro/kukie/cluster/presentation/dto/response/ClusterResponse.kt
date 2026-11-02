package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterStatus
import java.time.LocalDateTime
import java.util.UUID

data class ClusterResponse(
    val id: UUID,
    val teamId: UUID,
    val registeredBy: UUID,
    val name: String,
    val provider: ClusterProvider,
    val apiServer: String,
    val context: String,
    val namespace: String,
    val fingerprint: String,
    val status: ClusterStatus,
    val insecure: Boolean,
    val lastCheckedAt: LocalDateTime?,
    val hasGpu: Boolean?,
    /** EKS 에 붙은 권한 레벨(full·limited). kubeconfig 로 등록한 클러스터는 null. */
    val accessLevel: AccessLevel?,
    /**
     * 스택의 레벨로 바꿔 붙이지 못한 때. 그동안 accessLevel 은 바꾸기 전 레벨이다. 문제가 없으면 null.
     * limited 로 내리다 실패했으면 관리자 정책을 떼어 accessLevel 이 full 이어도 실제 권한은 없을 수 있다.
     */
    val accessLevelFailedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(cluster: Cluster) = ClusterResponse(
            id = cluster.id,
            teamId = cluster.teamId,
            registeredBy = cluster.registeredBy,
            name = cluster.name,
            provider = cluster.provider,
            apiServer = cluster.apiServer,
            context = cluster.contextName,
            namespace = cluster.defaultNamespace,
            fingerprint = cluster.fingerprint,
            status = cluster.status,
            insecure = cluster.insecure,
            lastCheckedAt = cluster.lastCheckedAt,
            hasGpu = cluster.hasGpu,
            accessLevel = cluster.accessLevel,
            accessLevelFailedAt = cluster.accessLevelFailedAt,
            createdAt = cluster.createdAt,
            updatedAt = cluster.updatedAt,
        )
    }
}
