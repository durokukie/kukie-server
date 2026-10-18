package com.duro.kukie.support

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.EksClusterSummary

class FakeEksAccessGateway : EksAccessGateway, Resettable {

    var clusters: List<EksClusterSummary> = DEFAULT_CLUSTERS

    var endpoint: EksClusterEndpoint = DEFAULT_ENDPOINT

    /** 설정하면 모든 호출이 이 예외로 실패한다 (역할을 맡지 못함, 권한 없음 등). */
    var failure: RuntimeException? = null

    val targets = mutableListOf<AwsTarget>()

    val registrations = mutableListOf<Registration>()

    override fun verify(target: AwsTarget) {
        record(target)
    }

    override fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary> {
        record(target)
        return clusters.filter { it.region in regions }
    }

    override fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint {
        record(target)
        return endpoint
    }

    override fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String) {
        record(target)
        registrations += Registration(target, region, clusterName, namespace)
    }

    override fun issueToken(target: AwsTarget, region: String, clusterName: String): String {
        record(target)
        return tokenFor(clusterName)
    }

    private fun record(target: AwsTarget) {
        failure?.let { throw it }
        targets += target
    }

    override fun clear() {
        clusters = DEFAULT_CLUSTERS
        endpoint = DEFAULT_ENDPOINT
        failure = null
        targets.clear()
        registrations.clear()
    }

    data class Registration(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
        val namespace: String,
    )

    companion object {
        const val DEFAULT_REGION = "ap-northeast-2"
        const val DEFAULT_CLUSTER_NAME = "demo"

        val DEFAULT_CLUSTERS = listOf(
            EksClusterSummary(name = DEFAULT_CLUSTER_NAME, region = DEFAULT_REGION, status = "ACTIVE", version = "1.33"),
            EksClusterSummary(name = "elsewhere", region = "us-east-1", status = "ACTIVE", version = "1.32"),
        )

        val DEFAULT_ENDPOINT = EksClusterEndpoint(
            endpoint = "https://ABCDEF0123456789.gr7.ap-northeast-2.eks.amazonaws.com",
            caData = "ZWtzLWNh",
        )

        fun tokenFor(clusterName: String): String = "k8s-aws-v1.fake-$clusterName"
    }
}
