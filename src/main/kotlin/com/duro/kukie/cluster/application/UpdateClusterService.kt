package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.UpdateClusterCommand
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.KubeconfigParser
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.saveAndFlushOrThrowNameTaken
import com.duro.kukie.cluster.exception.ClusterProviderMismatchException
import com.duro.kukie.cluster.exception.NothingToUpdateException
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class UpdateClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val kubeconfigParser: KubeconfigParser,
    private val credentialCipher: CredentialCipher,
    private val transactionTemplate: TransactionTemplate,
) {

    /** kubeconfig 검사(DNS)는 트랜잭션 밖에서 하고, 그사이 바뀐 값을 덮지 않게 트랜잭션에서 다시 읽어 고친다. */
    operator fun invoke(command: UpdateClusterCommand): ClusterResponse {
        val current = clusterPermission.requireWritable(command.clusterId, command.userId)
        if (command.name == null && command.namespace == null && command.kubeconfig == null) {
            throw NothingToUpdateException()
        }
        // EKS 접근 정책은 등록한 namespace 에만 붙어 있어 연결 정보와 namespace 를 여기서 바꾸면 접속이 끊긴다
        if (current.provider == ClusterProvider.EKS && (command.kubeconfig != null || command.namespace != null)) {
            throw ClusterProviderMismatchException()
        }
        val parsed = command.kubeconfig?.let { kubeconfigParser.parse(it, command.context) }

        return transactionTemplate.execute {
            val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
            command.name?.let(cluster::rename)
            command.namespace?.let(cluster::changeDefaultNamespace)
            parsed?.let {
                cluster.replaceConnection(
                    apiServer = it.apiServer,
                    caData = it.caData,
                    insecure = it.insecure,
                    credential = credentialCipher.encrypt(it.credential),
                    fingerprint = it.fingerprint,
                )
            }

            ClusterResponse.from(clusterRepository.saveAndFlushOrThrowNameTaken(cluster))
        }
    }
}
