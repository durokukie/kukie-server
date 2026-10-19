package com.duro.kukie.cluster.application.port.out

interface EksAccessGateway {

    /** 역할을 맡을 수 있는지만 본다. 못 맡으면 AwsRoleNotAssumableException. */
    fun verify(target: AwsTarget)

    fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary>

    fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint

    /** 역할 자신을 클러스터의 access entry 로 넣고 그 namespace 에 Edit 정책을 붙인다. 이미 있으면 그대로 둔다. */
    fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String)

    /**
     * 역할 자신의 access entry 를 정리한다. keepNamespaces 가 비면 entry 를 지우고(없으면 그대로), 남은 namespace 가
     * 있으면 Edit 정책 범위를 그 namespace 들로 좁힌다 — 같은 EKS 를 다른 namespace 로 연결한 등록이 끊기지 않게.
     */
    fun unregisterSelf(target: AwsTarget, region: String, clusterName: String, keepNamespaces: Set<String>)

    /** 버전·엔드포인트와 애드온·노드 그룹·서브넷을 읽는다. v3 권한이 없으면 inventory 를 비워 돌려준다. */
    fun describeSetup(target: AwsTarget, region: String, clusterName: String): EksClusterSetup

    fun issueToken(target: AwsTarget, region: String, clusterName: String): String
}
