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
            control = connection?.gpuControlOn(credential.clusterName) == true,
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
    /** 노드 수를 바꿀 수 있는지. 고객 역할의 AWS 권한이 따로 필요해 스택의 GpuNodeControl·GpuClusterPattern 까지 본다. */
    val control: Boolean,
    /** 모델을 시작·중지할 수 있는지. 쿠버네티스 API 만 쓰므로 AccessLevel 이 full 이면 된다. */
    val serving: Boolean,
)
