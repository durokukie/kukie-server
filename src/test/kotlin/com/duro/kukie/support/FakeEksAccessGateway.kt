package com.duro.kukie.support

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksAddon
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.EksClusterInventory
import com.duro.kukie.cluster.application.port.out.EksClusterSetup
import com.duro.kukie.cluster.application.port.out.EksClusterSummary
import com.duro.kukie.cluster.application.port.out.EksNodegroup
import com.duro.kukie.cluster.application.port.out.EksNodegroupState
import com.duro.kukie.cluster.application.port.out.EksSubnet
import com.duro.kukie.cluster.application.port.out.ModelChatStream
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.application.port.out.ServedModelManifest
import com.duro.kukie.cluster.application.port.out.ServedModelState
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import java.time.Duration
import java.time.Instant

class FakeEksAccessGateway : EksAccessGateway, Resettable {

    var clusters: List<EksClusterSummary> = DEFAULT_CLUSTERS

    var endpoint: EksClusterEndpoint = DEFAULT_ENDPOINT

    var setup: EksClusterSetup = DEFAULT_SETUP

    val setupRequests = mutableListOf<SetupRequest>()

    /** 설정하면 모든 호출이 이 예외로 실패한다 (역할을 맡지 못함, 권한 없음 등). */
    var failure: RuntimeException? = null

    /** 이 이름의 EKS 에 정책을 붙이는 호출(registerSelf·changeAccessLevel)만 거부된다. */
    var deniedClusters: Set<String> = emptySet()

    /** 이 namespace 를 붙이는 registerSelf 만 거부된다. */
    var deniedNamespaces: Set<String> = emptySet()

    /** 설정하면 unregisterSelf 가 이 예외로 실패한다. */
    var unregisterFailure: RuntimeException? = null

    /** selfEntrySettled 가 돌려줄 값. */
    var selfEntrySettled: Boolean = true

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

    /** 있으면 namespaceStatus 보다 먼저 하나씩 돌려준다. */
    val namespaceStatuses = ArrayDeque<NamespaceStatus>()

    val namespaceChecks = mutableListOf<NamespaceCheck>()

    /** registerSelf·namespaceStatus·unregisterSelf 를 부른 순서대로. */
    val accessChanges = mutableListOf<Any>()

    /** listNodegroupStates·describeNodegroupState 가 돌려줄 노드 그룹. */
    var nodegroupStates: List<EksNodegroupState> = DEFAULT_NODEGROUP_STATES

    /** 설정하면 노드 그룹을 읽는 호출이 이 예외로 실패한다. */
    var nodegroupReadFailure: RuntimeException? = null

    /** 설정하면 updateNodegroupDesired 가 이 예외로 실패한다. */
    var nodegroupUpdateFailure: RuntimeException? = null

    val nodegroupUpdates = mutableListOf<NodegroupUpdate>()

    /** hasServedModel 이 돌려줄 값. */
    var servedModelPresent: Boolean = false

    /** 설정하면 ServedModel 을 다루는 쿠버네티스 호출이 이 예외로 실패한다. */
    var servedModelFailure: RuntimeException? = null

    /** 설정하면 createServedModel 만 이 예외로 실패한다. */
    var servedModelCreateFailure: RuntimeException? = null

    val servedModelCreations = mutableListOf<ServedModelManifest>()

    val servedModelDeletions = mutableListOf<ServedModelDeletion>()

    /** 노드 그룹·ServedModel 을 다루는 호출에 넘어온 API 서버. */
    val servedModelEndpoints = mutableListOf<EksClusterEndpoint>()

    /** getServedModel 이 돌려줄 값. null 이면 ServedModelNotFoundException. */
    var servedModelState: ServedModelState? = READY_MODEL

    val keepUntilWrites = mutableListOf<KeepUntilWrite>()

    /** keepServedModelUntil 이 이 횟수만큼 충돌(false)을 돌려준다. */
    var keepUntilConflicts: Int = 0

    val measureWrites = mutableListOf<MeasureWrite>()

    /** requestServedModelMeasure 가 이 횟수만큼 충돌(false)을 돌려준다. */
    var measureConflicts: Int = 0

    /** 설정하면 측정 요청이 충돌할 때 ServedModel 이 이 값으로 바뀐다. 다시 읽는지 볼 때 쓴다. */
    var servedModelStateAfterMeasureConflict: ServedModelState? = null

    /** 설정하면 대화 관련 호출을 실제 구현과 로컬 가짜 API 서버로 넘긴다. */
    var servingBackend: EksAccessGateway? = null
    var servingBackendEndpoint: EksClusterEndpoint? = null

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
        if (clusterName in deniedClusters || namespace in deniedNamespaces) throw EksAccessDeniedException()
        registrations += Registration(target, region, clusterName, namespace, accessLevel)
        accessChanges += Registration(target, region, clusterName, namespace, accessLevel)
    }

    override fun selfEntrySettled(target: AwsTarget, region: String, clusterName: String): Boolean {
        record(target)
        return selfEntrySettled
    }

    override fun unregisterSelf(
        target: AwsTarget,
        region: String,
        clusterName: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        record(target)
        unregisterFailure?.let { throw it }
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
        return namespaceStatuses.removeFirstOrNull() ?: namespaceStatus
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

    override fun listNodegroupStates(target: AwsTarget, region: String, clusterName: String): List<EksNodegroupState> {
        record(target)
        nodegroupReadFailure?.let { throw it }
        return nodegroupStates
    }

    override fun describeNodegroupState(
        target: AwsTarget,
        region: String,
        clusterName: String,
        nodegroupName: String,
    ): EksNodegroupState? {
        record(target)
        nodegroupReadFailure?.let { throw it }
        return nodegroupStates.firstOrNull { it.name == nodegroupName }
    }

    override fun updateNodegroupDesired(
        target: AwsTarget,
        region: String,
        clusterName: String,
        current: EksNodegroupState,
        desired: Int,
    ) {
        record(target)
        nodegroupUpdateFailure?.let { throw it }
        nodegroupUpdates += NodegroupUpdate(target, region, clusterName, current.name, desired, current.min, current.max)
    }

    override fun hasServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
    ): Boolean {
        record(target)
        servedModelFailure?.let { throw it }
        return servedModelPresent
    }

    override fun createServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        manifest: ServedModelManifest,
    ) {
        record(target)
        servedModelFailure?.let { throw it }
        servedModelCreateFailure?.let { throw it }
        servedModelEndpoints += endpoint
        servedModelCreations += manifest
    }

    override fun deleteServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    ) {
        record(target)
        servedModelFailure?.let { throw it }
        servedModelEndpoints += endpoint
        servedModelDeletions += ServedModelDeletion(namespace, name)
    }

    override fun getServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    ): ServedModelState {
        servingBackend?.let { return it.getServedModel(target, region, clusterName, servingBackendEndpoint!!, namespace, name) }
        record(target)
        servedModelFailure?.let { throw it }
        return servedModelState ?: throw ServedModelNotFoundException()
    }

    override fun keepServedModelUntil(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        keepUntil: Instant,
        resourceVersion: String,
    ): Boolean {
        record(target)
        servedModelFailure?.let { throw it }
        if (keepUntilConflicts > 0) {
            keepUntilConflicts--
            return false
        }
        keepUntilWrites += KeepUntilWrite(namespace, name, keepUntil, resourceVersion)
        return true
    }

    override fun requestServedModelMeasure(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        request: String,
        resourceVersion: String,
    ): Boolean {
        record(target)
        servedModelFailure?.let { throw it }
        if (measureConflicts > 0) {
            measureConflicts--
            servedModelStateAfterMeasureConflict?.let { servedModelState = it }
            return false
        }
        measureWrites += MeasureWrite(namespace, name, request, resourceVersion)
        return true
    }

    override fun openModelChat(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
        body: ByteArray,
        timeout: Duration,
    ): ModelChatStream {
        val backend = checkNotNull(servingBackend) { "대화 흐름은 ModelChatStreamIntegrationTest 에서 실제 구현으로 시험한다" }
        return backend.openModelChat(target, region, clusterName, servingBackendEndpoint!!, namespace, name, body, timeout)
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
        deniedNamespaces = emptySet()
        unregisterFailure = null
        selfEntrySettled = true
        targets.clear()
        roleExists = true
        roleChecks.clear()
        registrations.clear()
        unregistrations.clear()
        levelChanges.clear()
        namespaceStatus = NamespaceStatus.EXISTS
        namespaceStatuses.clear()
        namespaceChecks.clear()
        accessChanges.clear()
        nodegroupStates = DEFAULT_NODEGROUP_STATES
        nodegroupReadFailure = null
        nodegroupUpdateFailure = null
        nodegroupUpdates.clear()
        servedModelPresent = false
        servedModelFailure = null
        servedModelCreateFailure = null
        servedModelCreations.clear()
        servedModelDeletions.clear()
        servedModelEndpoints.clear()
        servedModelState = READY_MODEL
        keepUntilWrites.clear()
        keepUntilConflicts = 0
        measureWrites.clear()
        measureConflicts = 0
        servedModelStateAfterMeasureConflict = null
        servingBackend = null
        servingBackendEndpoint = null
    }

    data class KeepUntilWrite(
        val namespace: String,
        val name: String,
        val keepUntil: Instant,
        val resourceVersion: String,
    )

    data class MeasureWrite(
        val namespace: String,
        val name: String,
        val request: String,
        val resourceVersion: String,
    )

    data class NodegroupUpdate(
        val target: AwsTarget,
        val region: String,
        val clusterName: String,
        val nodegroupName: String,
        val desired: Int,
        val min: Int,
        val max: Int,
    )

    data class ServedModelDeletion(
        val namespace: String,
        val name: String,
    )

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

        val READY_MODEL = ServedModelState(
            model = "openai/gpt-oss-20b",
            phase = "Ready",
            deleting = false,
            resourceVersion = "7",
            keepUntil = null,
            stopsAt = null,
        )

        val DEFAULT_NODEGROUP_STATES = listOf(
            EksNodegroupState(name = "general", instanceTypes = listOf("t3.large"), status = "ACTIVE", desired = 2, min = 1, max = 3),
            EksNodegroupState(name = "gpu", instanceTypes = listOf("g6e.xlarge"), status = "ACTIVE", desired = 0, min = 0, max = 2),
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
