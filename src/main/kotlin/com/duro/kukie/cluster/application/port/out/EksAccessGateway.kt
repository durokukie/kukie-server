package com.duro.kukie.cluster.application.port.out

interface EksAccessGateway {

    /** 역할을 맡을 수 있는지만 본다. 못 맡으면 AwsRoleNotAssumableException. */
    fun verify(target: AwsTarget)

    fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary>

    fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint

    /** 역할 자신을 클러스터의 access entry 로 넣고 그 namespace 에 Edit 정책을 붙인다. 이미 있으면 그대로 둔다. */
    fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String)

    fun issueToken(target: AwsTarget, region: String, clusterName: String): String
}
