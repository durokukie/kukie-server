package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.RegisterClusterCommand
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.KubeconfigParser
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.saveAndFlushOrThrowNameTaken
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service

@Service
class RegisterClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val kubeconfigParser: KubeconfigParser,
    private val credentialCipher: CredentialCipher,
) {

    /** kubeconfig 검사가 DNS 를 기다리는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: RegisterClusterCommand): ClusterResponse {
        clusterPermission.requireTeamAdmin(command.teamId, command.userId)
        val parsed = kubeconfigParser.parse(command.kubeconfig, command.context)

        val cluster = clusterRepository.saveAndFlushOrThrowNameTaken(
            Cluster(
                teamId = command.teamId,
                registeredBy = command.userId,
                name = command.name,
                apiServer = parsed.apiServer,
                caData = parsed.caData,
                insecure = parsed.insecure,
                credential = credentialCipher.encrypt(parsed.credential),
                contextName = parsed.contextName,
                defaultNamespace = command.namespace ?: parsed.namespace,
                fingerprint = parsed.fingerprint,
            ),
        )

        return ClusterResponse.from(cluster)
    }
}
