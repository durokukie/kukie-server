package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.domain.findByTeamIdOrThrow
import com.duro.kukie.cluster.presentation.dto.response.AwsDisconnectionResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * AWS 연결 해제. Kukie 는 고객 스택을 지울 권한이 없어(역할이 자기 자신을 지우게 된다) 우리 쪽만 정리하고,
 * 고객이 스택을 지울 콘솔 주소를 돌려준다.
 *
 * 등록한 클러스터와 ExternalId 는 남긴다 — 다시 연결하면 같은 스택 이름으로 만들고, 스택 알림이 access entry 를 다시 붙인다.
 * 그 사이 클러스터는 연결할 때 오류가 난다.
 */
@Service
class DisconnectAwsConnectionService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterRepository: ClusterRepository,
    private val clusterPermission: ClusterPermission,
    private val eksAccessGateway: EksAccessGateway,
    private val credentialCipher: CredentialCipher,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    private val log = logger()

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다(저장은 save 한 번). */
    operator fun invoke(teamId: UUID, userId: UUID): AwsDisconnectionResponse {
        clusterPermission.requireTeamAdmin(teamId, userId)
        val connection = awsConnectionRepository.findByTeamIdOrThrow(teamId)
        val stackName = clusterAwsProperties.stackNameFor(connection.externalId)
        val stackConsoleUrl = StackConsoleUrl.of(connection.stackId, stackName, clusterAwsProperties.consoleRegion)

        releaseEksAccess(teamId)
        // 정리와 저장 사이에 다시 만든 스택의 알림이 끼어들면 붙인 entry 와 달리 연결이 비워진다. 창이 짧아 받아들인다
        connection.disconnect()
        awsConnectionRepository.save(connection)

        return AwsDisconnectionResponse(stackConsoleUrl = stackConsoleUrl, stackName = stackName)
    }

    /**
     * 팀의 EKS 마다 access entry 를 지운다. 같은 EKS 를 여러 namespace 로 등록했어도 entry 는 하나라 한 번만 부른다.
     *
     * 정리하지 못해도 해제는 막지 않는다. 고객이 스택을 지우면 역할이 사라져 그 entry 로는 아무도 들어오지 못하고,
     * 권한·네트워크 오류는 고객이 EKS 콘솔에서 지울 수 있다.
     */
    private fun releaseEksAccess(teamId: UUID) {
        val credentials = clusterRepository.findAllByTeamIdAndProvider(teamId, ClusterProvider.EKS).mapNotNull { cluster ->
            try {
                EksCredential.from(credentialCipher.decrypt(cluster.credential))
            } catch (exception: RuntimeException) {
                log.warn("EKS 클러스터 연결 정보를 읽지 못했습니다: cluster={} reason={}", cluster.id, exception.javaClass.simpleName)
                null
            }
        }
        // 계정·ExternalId·리전·클러스터 이름이 같으면 같은 EKS 다(DeleteClusterService.sameEks 와 같은 기준)
        for (credential in credentials.distinct()) {
            try {
                eksAccessGateway.unregisterSelf(credential.target, credential.region, credential.clusterName, emptySet())
            } catch (exception: RuntimeException) {
                // 역할 없음·권한 없음(BusinessException), AWS SDK 오류 — 어느 쪽이든 해제는 계속한다
                log.warn(
                    "EKS access entry 를 정리하지 못했습니다: region={} cluster={} reason={}",
                    credential.region,
                    credential.clusterName,
                    exception.javaClass.simpleName,
                )
            }
        }
    }
}
