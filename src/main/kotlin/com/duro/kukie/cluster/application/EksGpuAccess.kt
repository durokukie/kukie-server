package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.exception.EksClusterNotReadyException
import com.duro.kukie.cluster.exception.GpuNotSupportedException
import org.springframework.stereotype.Component

@Component
class EksGpuAccess(
    private val credentialCipher: CredentialCipher,
    private val awsConnectionRepository: AwsConnectionRepository,
) {

    /** 클러스터를 등록한 연결을 스택 알림 값으로 읽는다. 해제했거나 다른 계정으로 바뀐 연결이면 어떤 쓰기도 허용하지 않는다. */
    fun of(cluster: Cluster): GpuAccess {
        if (cluster.provider != ClusterProvider.EKS) throw GpuNotSupportedException()

        val credential = EksCredential.from(credentialCipher.decrypt(cluster.credential))
        val connection = connectionOf(cluster, credential)
        return GpuAccess(
            credential = credential,
            nodeControl = connection?.gpuNodeControlEnabled == true,
            serving = connection?.accessLevel == AccessLevel.FULL,
        )
    }

    fun endpointOf(cluster: Cluster): EksClusterEndpoint =
        EksClusterEndpoint(endpoint = cluster.apiServer, caData = cluster.caData ?: throw EksClusterNotReadyException())

    private fun connectionOf(cluster: Cluster, credential: EksCredential): AwsConnection? =
        awsConnectionRepository.findByExternalId(credential.externalId)
            ?.takeIf { it.teamId == cluster.teamId && it.accountId == credential.accountId }
}

data class GpuAccess(
    val credential: EksCredential,
    val nodeControl: Boolean,
    val serving: Boolean,
)
