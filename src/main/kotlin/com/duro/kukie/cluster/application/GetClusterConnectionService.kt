package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.exception.ClusterChangedException
import com.duro.kukie.cluster.presentation.dto.response.ClusterConnectionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetClusterConnectionService(
    private val clusterPermission: ClusterPermission,
    private val credentialCipher: CredentialCipher,
) {

    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID, expectedFingerprint: String?): ClusterConnectionResponse {
        val cluster = clusterPermission.requireReadable(clusterId, userId)
        if (!expectedFingerprint.isNullOrBlank() && expectedFingerprint != cluster.fingerprint) {
            throw ClusterChangedException()
        }

        return ClusterConnectionResponse.of(cluster, credentialCipher.decrypt(cluster.credential))
    }
}
