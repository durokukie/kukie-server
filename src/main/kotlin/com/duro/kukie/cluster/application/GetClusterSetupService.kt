package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.presentation.dto.response.ClusterSetupResponse
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetClusterSetupService(
    private val clusterPermission: ClusterPermission,
    private val credentialCipher: CredentialCipher,
    private val eksAccessGateway: EksAccessGateway,
) {

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID): ClusterSetupResponse {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        if (cluster.provider != ClusterProvider.EKS) return ClusterSetupResponse.unsupported()

        val credential = EksCredential.from(credentialCipher.decrypt(cluster.credential))
        return ClusterSetupResponse.from(eksAccessGateway.describeSetup(credential.target, credential.region, credential.clusterName))
    }
}
