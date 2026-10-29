package com.duro.kukie.cluster.application.port.out

import com.duro.kukie.cluster.domain.AccessLevel

interface EksAccessGateway {

    /**
     * 역할을 맡을 수 있는지만 본다. 못 맡으면 AwsRoleNotAssumableException. 스택 알림 때 부르므로(역할을 다시 만들었을 수 있다)
     * 캐시된 세션을 버리고 새로 맡는다.
     */
    fun verify(target: AwsTarget)

    /**
     * 역할이 아직 있는지 캐시를 거치지 않고 실제로 맡아 본다. 그 대상의 캐시된 세션도 버리고, 새로 얻은 세션은 담지 않는다.
     * 맡으면 true, 거부(403)되면 false — 우리 ExternalId 로 거부되면 역할이 지워진 것이다. 그 밖의 오류(스로틀링·네트워크)는 던진다.
     */
    fun roleExists(target: AwsTarget): Boolean

    fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary>

    fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint

    /**
     * 역할 자신을 클러스터의 access entry 로 넣고 레벨의 정책을 붙인다 — FULL 이면 클러스터 관리자(클러스터 전체),
     * LIMITED 면 그 namespace 에 Edit. 이미 있으면 그대로 둔다.
     */
    fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String, accessLevel: AccessLevel)

    /**
     * 역할 자신의 access entry 를 정리한다. keepNamespaces 가 비면 entry 를 지우고(없으면 그대로), 남은 namespace 가
     * 있으면 LIMITED 는 Edit 정책 범위를 그 namespace 들로 좁힌다 — 같은 EKS 를 다른 namespace 로 연결한 등록이 끊기지 않게.
     * FULL 은 클러스터 전체 범위라 그대로 둔다.
     */
    fun unregisterSelf(
        target: AwsTarget,
        region: String,
        clusterName: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    )

    /**
     * 스택의 레벨이 바뀌었을 때 이미 등록한 EKS 의 정책을 바꾼다. 새 레벨의 정책을 먼저 붙이고(LIMITED 면 namespaces 범위)
     * 다른 레벨의 정책을 뗀다. entry 가 없으면 만든다.
     */
    fun changeAccessLevel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        namespaces: Set<String>,
        accessLevel: AccessLevel,
    )

    /**
     * namespace 의 default ServiceAccount 를 쿠버네티스 API 로 읽어 namespace 가 있는지 본다.
     * waitForGrant 면 막 붙인 권한이 퍼질 때까지 401·403 을 몇 번 다시 묻는다.
     */
    fun namespaceStatus(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        waitForGrant: Boolean,
    ): NamespaceStatus

    /** 버전·엔드포인트와 애드온·노드 그룹·서브넷을 읽는다. v3 권한이 없으면 inventory 를 비워 돌려준다. */
    fun describeSetup(target: AwsTarget, region: String, clusterName: String): EksClusterSetup

    fun issueToken(target: AwsTarget, region: String, clusterName: String): String
}
