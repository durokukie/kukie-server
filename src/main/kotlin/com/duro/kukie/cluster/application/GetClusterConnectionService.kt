package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.exception.ClusterChangedException
import com.duro.kukie.cluster.presentation.dto.response.ClusterConnectionResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetClusterConnectionService(
    private val clusterPermission: ClusterPermission,
    private val credentialCipher: CredentialCipher,
    private val eksAccessGateway: EksAccessGateway,
) {

    /** EKS 토큰을 만들려고 AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID, expectedFingerprint: String?): ClusterConnectionResponse {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        if (!expectedFingerprint.isNullOrBlank() && expectedFingerprint != cluster.fingerprint) {
            throw ClusterChangedException()
        }

        val stored = credentialCipher.decrypt(cluster.credential)
        val credential = when (cluster.provider) {
            ClusterProvider.GENERIC -> stored
            ClusterProvider.EKS -> EksCredential.from(stored).let {
                mapOf("token" to eksAccessGateway.issueToken(it.target, it.region, it.clusterName))
            }
        }

        return ClusterConnectionResponse.of(cluster, credential)
    }
}
