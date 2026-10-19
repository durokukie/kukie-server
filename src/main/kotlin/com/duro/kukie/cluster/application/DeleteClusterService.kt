package com.duro.kukie.cluster.application

import com.duro.kukie.chat.domain.ChatSessionRepository
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterInUseException
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val chatSessionRepository: ChatSessionRepository,
    private val eksAccessGateway: EksAccessGateway,
    private val credentialCipher: CredentialCipher,
) {

    private val log = logger()

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다(삭제는 delete 한 번). */
    operator fun invoke(clusterId: UUID, userId: UUID) {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        if (chatSessionRepository.existsByClusterId(cluster.id)) throw ClusterInUseException()
        if (cluster.provider == ClusterProvider.EKS) releaseEksAccess(cluster)

        clusterRepository.delete(cluster)
    }

    /**
     * 연결할 때 고객 EKS 에 붙인 access entry 를 정리한다. 같은 팀이 같은 EKS 를 다른 namespace 로 또 연결해 두었으면
     * (entry 는 클러스터마다 하나라 namespace 들이 한 entry 에 모인다) 그 namespace 만 남긴다.
     *
     * 정리하지 못해도 삭제는 막지 않는다. 고객이 스택을 지워 역할을 못 빌리면 그 entry 로는 어차피 아무도 들어오지 못하고,
     * 권한·네트워크 오류는 고객이 EKS 콘솔에서 지울 수 있다.
     */
    private fun releaseEksAccess(cluster: Cluster) {
        val credential = EksCredential.from(credentialCipher.decrypt(cluster.credential))
        val keepNamespaces = clusterRepository.findAllByTeamIdAndProvider(cluster.teamId, ClusterProvider.EKS)
            .filter { it.id != cluster.id }
            .filter { sameEks(EksCredential.from(credentialCipher.decrypt(it.credential)), credential) }
            .map { it.defaultNamespace }
            .toSet()
        try {
            eksAccessGateway.unregisterSelf(credential.target, credential.region, credential.clusterName, keepNamespaces)
        } catch (exception: RuntimeException) {
            // 역할 없음·권한 없음(BusinessException)과 AWS SDK 오류(SdkException) 모두 — 삭제는 계속한다
            log.warn("EKS access entry 를 정리하지 못했습니다: cluster={} reason={}", cluster.id, exception.javaClass.simpleName)
        }
    }

    private fun sameEks(a: EksCredential, b: EksCredential): Boolean =
        a.accountId == b.accountId && a.externalId == b.externalId && a.region == b.region && a.clusterName == b.clusterName
}
