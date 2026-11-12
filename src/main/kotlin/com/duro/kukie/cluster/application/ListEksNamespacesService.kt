package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.ListEksNamespacesCommand
import com.duro.kukie.cluster.application.port.out.ApiServerAddressChecker
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.AwsRegionNotSupportedException
import com.duro.kukie.cluster.exception.EksEndpointRejectedException
import com.duro.kukie.cluster.exception.NamespaceListNotSupportedException
import com.duro.kukie.cluster.presentation.dto.response.EksNamespacesResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 등록 화면에서 고를 namespace 를 읽는다. full 연결만 된다 — limited 의 Edit 정책(namespace 범위)으로는 namespace 목록을 읽을 수 없다.
 *
 * namespace 는 쿠버네티스 API 에 물어야 해서 Kukie 역할의 access entry 가 있어야 한다. 그래서 잠깐 붙이고, 읽고, 이 팀이 그 EKS 를
 * 아직 등록하지 않았으면 바로 뗀다. 템플릿이 고객에게 "Kukie 에서 등록한 클러스터에만 관리자로 들어간다" 고 약속하므로, 화면을 닫거나
 * 앱이 꺼져도 등록하지 않은 클러스터에 관리자 entry 가 남지 않게 하려는 것이다. 목록은 앱이 기억한다.
 *
 * 같은 EKS 를 그 사이 다른 관리자가 등록하고 있으면 그 등록이 붙인 entry 를 뗄 수 있다. namespace 를 확인하기 전에 떼면 등록이
 * 권한 대기(409)로 끝나 다시 부르면 되고, 떼는 사이 저장까지 마쳤으면 뗀 뒤 다시 보고 붙여 둔다. 등록의 확인과 저장 사이에 떼고 다시 보기가
 * 모두 끼는 아주 좁은 틈만 남는다(그러면 그 클러스터는 401 로 끊겨 다시 등록해야 한다).
 */
@Service
class ListEksNamespacesService(
    private val clusterPermission: ClusterPermission,
    private val eksAccessGateway: EksAccessGateway,
    private val eksNamespaces: EksNamespaces,
    private val apiServerAddressChecker: ApiServerAddressChecker,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    private val log = logger()

    /** AWS 와 클러스터를 기다리는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: ListEksNamespacesCommand): EksNamespacesResponse {
        val connection = clusterPermission.requireWritableAwsConnection(command.connectionId, command.userId)
        connection.requireAccount(command.accountId)
        if (command.region !in clusterAwsProperties.regions) throw AwsRegionNotSupportedException()
        if (connection.accessLevel != AccessLevel.FULL) throw NamespaceListNotSupportedException()
        val credential = EksCredential(
            accountId = command.accountId,
            externalId = connection.externalId,
            region = command.region,
            clusterName = command.clusterName,
        )

        val endpoint = eksAccessGateway.describeCluster(credential.target, credential.region, credential.clusterName)
        apiServerAddressChecker.rejectionOf(endpoint.endpoint)?.let { throw EksEndpointRejectedException(it) }
        try {
            attachFull(credential)
            val names = eksAccessGateway.listNamespaces(credential.target, credential.region, credential.clusterName, endpoint)
            return EksNamespacesResponse(names.filterNot { it.startsWith(SYSTEM_PREFIX) }.distinct().sorted())
        } finally {
            releaseUnregistered(connection.teamId, credential)
        }
    }

    /** 이 팀이 등록한 적 없는 EKS 면 붙인 entry 를 뗀다. 실패해도 목록 응답은 그대로 준다. */
    private fun releaseUnregistered(teamId: UUID, credential: EksCredential) {
        try {
            if (eksNamespaces.registeredOn(teamId, credential).isNotEmpty()) return
            eksAccessGateway.unregisterSelf(credential.target, credential.region, credential.clusterName, emptySet(), AccessLevel.FULL)
            // 떼는 사이 같은 EKS 의 등록이 저장됐으면 그 등록의 entry 이므로 다시 붙인다
            if (eksNamespaces.registeredOn(teamId, credential).isNotEmpty()) attachFull(credential)
        } catch (exception: RuntimeException) {
            log.warn("목록을 읽으려고 붙인 access entry 를 떼지 못했습니다: team={} reason={}", teamId, exception.javaClass.simpleName)
        }
    }

    // FULL 은 클러스터 전체 범위라 namespace 를 쓰지 않는다
    private fun attachFull(credential: EksCredential) =
        eksAccessGateway.registerSelf(credential.target, credential.region, credential.clusterName, UNUSED_NAMESPACE, AccessLevel.FULL)

    companion object {
        private const val SYSTEM_PREFIX = "kube-"
        private const val UNUSED_NAMESPACE = "default"
    }
}
