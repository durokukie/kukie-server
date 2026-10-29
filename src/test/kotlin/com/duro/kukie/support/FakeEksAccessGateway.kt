package com.duro.kukie.support

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksAddon
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.EksClusterInventory
import com.duro.kukie.cluster.application.port.out.EksClusterSetup
import com.duro.kukie.cluster.application.port.out.EksClusterSummary
import com.duro.kukie.cluster.application.port.out.EksNodegroup
import com.duro.kukie.cluster.application.port.out.EksSubnet
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.EksAccessDeniedException

class FakeEksAccessGateway : EksAccessGateway, Resettable {

    var clusters: List<EksClusterSummary> = DEFAULT_CLUSTERS

    var endpoint: EksClusterEndpoint = DEFAULT_ENDPOINT

    var setup: EksClusterSetup = DEFAULT_SETUP

    val setupRequests = mutableListOf<SetupRequest>()

    /** 설정하면 모든 호출이 이 예외로 실패한다 (역할을 맡지 못함, 권한 없음 등). */
    var failure: RuntimeException? = null

    /** 이 이름의 EKS 에 정책을 붙이는 호출(registerSelf·changeAccessLevel)만 거부된다. */
    var deniedClusters: Set<String> = emptySet()

    val targets = mutableListOf<AwsTarget>()

    /** roleExists 가 돌려줄 값. */
    var roleExists: Boolean = true

    /** roleExists 를 부른 대상. 캐시를 거치지 않는 확인이 몇 번 갔는지 본다. */
    val roleChecks = mutableListOf<AwsTarget>()

    val registrations = mutableListOf<Registration>()

    val unregistrations = mutableListOf<Unregistration>()

    val levelChanges = mutableListOf<LevelChange>()

    /** namespaceStatus 가 돌려줄 값. */
    var namespaceStatus: NamespaceStatus = NamespaceStatus.EXISTS

    val namespaceChecks = mutableListOf<NamespaceCheck>()

    /** registerSelf·namespaceStatus·unregisterSelf 를 부른 순서대로. */
    val accessChanges = mutableListOf<Any>()

    override fun verify(target: AwsTarget) {
        record(target)
    }

    override fun roleExists(target: AwsTarget): Boolean {
        failure?.let { throw it }
        roleChecks += target
        return roleExists
    }

    override fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary> {
        record(target)
        return clusters.filter { it.region in regions }
    }

    override fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint {
        record(target)
        return endpoint
    }

    override fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String, accessLevel: AccessLevel) {
        record(target)
        if (clusterName in deniedClusters) throw EksAccessDeniedException()
        registrations += Registration(target, region, clusterName, namespace, accessLevel)
        accessChanges += Registration(target, region, clusterName, namespace, accessLevel)
    }

    override fun unregisterSelf(
        target: AwsTarget,
        region: String,
        clusterName: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        record(target)
        unregistrations += Unregistration(target, region, clusterName, keepNamespaces, accessLevel)
        accessChanges += Unregistration(target, region, clusterName, keepNamespaces, accessLevel)
    }

    override fun changeAccessLevel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        namespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        record(target)
        if (clusterName in deniedClusters) throw EksAccessDeniedException()
        levelChanges += LevelChange(target, region, clusterName, namespaces, accessLevel)
        accessChanges += LevelChange(target, region, clusterName, namespaces, accessLevel)
    }

    override fun namespaceStatus(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        waitForGrant: Boolean,
    ): NamespaceStatus {
        record(target)
        namespaceChecks += NamespaceCheck(namespace, waitForGrant)
        accessChanges += NamespaceCheck(namespace, waitForGrant)
        return namespaceStatus
    }

    override fun describeSetup(target: AwsTarget, region: String, clusterName: String): EksClusterSetup {
        record(target)
        setupRequests += SetupRequest(target, region, clusterName)
        return setup
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
        setup = DEFAULT_SETUP
        setupRequests.clear()
        failure = null
        deniedClusters = emptySet()
        targets.clear()
        roleExists = true
        roleChecks.clear()
        registrations.clear()
        unregistrations.clear()
        levelChanges.clear()
        namespaceStatus = NamespaceStatus.EXISTS
        namespaceChecks.clear()
        accessChanges.clear()
    }

    data class NamespaceCheck(
        val namespace: String,
        val waitForGrant: Boolean,
    )

    data class SetupRequest(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
    )

    data class Unregistration(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
        val keepNamespaces: Set<String>,
        val accessLevel: AccessLevel = AccessLevel.LIMITED,
    )

    data class Registration(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
        val namespace: String,
        val accessLevel: AccessLevel = AccessLevel.LIMITED,
    )

    data class LevelChange(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
        val namespaces: Set<String>,
        val accessLevel: AccessLevel,
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

        val DEFAULT_SETUP = EksClusterSetup(
            kubernetesVersion = "1.31",
            platformVersion = "eks.12",
            endpointPublic = true,
            endpointPrivate = false,
            serviceCidr = "172.20.0.0/16",
            inventory = EksClusterInventory(
                addons = listOf(
                    EksAddon(name = "coredns", version = "v1.11.3-eksbuild.1", status = "ACTIVE"),
                    EksAddon(name = "vpc-cni", version = "v1.19.0-eksbuild.1", status = "ACTIVE"),
                ),
                nodegroups = listOf(
                    EksNodegroup(
                        name = "ng-1",
                        instanceTypes = listOf("t3.large"),
                        capacityType = "ON_DEMAND",
                        desired = 2,
                        min = 1,
                        max = 3,
                        zones = listOf("ap-northeast-2a", "ap-northeast-2c"),
                    ),
                ),
                subnets = listOf(
                    EksSubnet(zone = "ap-northeast-2a", cidr = "10.60.0.0/19"),
                    EksSubnet(zone = "ap-northeast-2c", cidr = "10.60.32.0/19"),
                ),
            ),
        )

        fun tokenFor(clusterName: String): String = "k8s-aws-v1.fake-$clusterName"
    }
}
