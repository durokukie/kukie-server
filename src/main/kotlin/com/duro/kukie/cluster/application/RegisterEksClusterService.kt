package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.RegisterEksClusterCommand
import com.duro.kukie.cluster.application.port.out.ApiServerAddressChecker
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.Cluster
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.saveAndFlushOrThrowNameTaken
import com.duro.kukie.cluster.exception.AwsRegionNotSupportedException
import com.duro.kukie.cluster.exception.ClusterNameTakenException
import com.duro.kukie.cluster.exception.EksEndpointRejectedException
import com.duro.kukie.cluster.exception.InvalidNamespaceException
import com.duro.kukie.cluster.exception.NamespaceNotFoundException
import com.duro.kukie.cluster.presentation.dto.response.ClusterResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RegisterEksClusterService(
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val eksAccessGateway: EksAccessGateway,
    private val eksNamespaces: EksNamespaces,
    private val apiServerAddressChecker: ApiServerAddressChecker,
    private val credentialCipher: CredentialCipher,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    private val log = logger()

    /** AWS 와 DNS 를 기다리는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: RegisterEksClusterCommand): ClusterResponse {
        val connection = clusterPermission.requireWritableAwsConnection(command.connectionId, command.userId)
        connection.requireAccount(command.accountId)
        if (command.region !in clusterAwsProperties.regions) throw AwsRegionNotSupportedException()
        if (!isAllowedNamespace(command.namespace)) throw InvalidNamespaceException()
        // 고객 계정에 권한을 붙인 뒤 이름 중복으로 저장이 실패하면 권한만 남으므로 먼저 확인한다.
        // 동시 등록이 이 검사를 함께 통과하는 경우는 아래 저장의 unique 제약이 막는다.
        if (clusterRepository.existsByTeamIdAndName(connection.teamId, command.name)) throw ClusterNameTakenException()
        val credential = EksCredential(
            accountId = command.accountId,
            externalId = connection.externalId,
            region = command.region,
            clusterName = command.clusterName,
        )

        val endpoint = eksAccessGateway.describeCluster(credential.target, command.region, command.clusterName)
        apiServerAddressChecker.rejectionOf(endpoint.endpoint)?.let { throw EksEndpointRejectedException(it) }
        eksAccessGateway.registerSelf(credential.target, command.region, command.clusterName, command.namespace)
        val namespaceExists = eksAccessGateway.namespaceExists(
            credential.target,
            command.region,
            command.clusterName,
            endpoint,
            command.namespace,
            waitForGrant = true,
        )
        if (namespaceExists == false) {
            releaseNamespace(connection.teamId, credential)
            throw NamespaceNotFoundException(command.namespace)
        }

        val cluster = clusterRepository.saveAndFlushOrThrowNameTaken(
            Cluster(
                teamId = connection.teamId,
                registeredBy = command.userId,
                name = command.name,
                apiServer = endpoint.endpoint,
                caData = endpoint.caData,
                insecure = false,
                credential = credentialCipher.encrypt(credential.toMap()),
                contextName = command.clusterName,
                defaultNamespace = command.namespace,
                fingerprint = Cluster.fingerprintOf(endpoint.endpoint, endpoint.caData),
                provider = ClusterProvider.EKS,
            ),
        )

        return ClusterResponse.from(cluster)
    }

    /** 없는 namespace 에 붙인 권한을 거둔다. 실패해도 등록 거부는 그대로 알린다. */
    private fun releaseNamespace(teamId: UUID, credential: EksCredential) {
        try {
            val keepNamespaces = eksNamespaces.registeredOn(teamId, credential)
            eksAccessGateway.unregisterSelf(credential.target, credential.region, credential.clusterName, keepNamespaces)
        } catch (exception: RuntimeException) {
            log.warn("없는 namespace 의 권한을 거두지 못했습니다: team={} reason={}", teamId, exception.javaClass.simpleName)
        }
    }

    /** 고객 템플릿이 정확한 이름만 허용하므로 패턴(*·?)과 시스템 namespace 는 받지 않는다. */
    private fun isAllowedNamespace(namespace: String): Boolean =
        namespace.length <= MAX_NAMESPACE_LENGTH && DNS_LABEL.matches(namespace) && !namespace.startsWith(SYSTEM_PREFIX)

    companion object {
        private const val MAX_NAMESPACE_LENGTH = 63
        private const val SYSTEM_PREFIX = "kube-"
        private val DNS_LABEL = Regex("[a-z0-9]([-a-z0-9]*[a-z0-9])?")
    }
}
