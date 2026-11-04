package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAddon
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
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
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.EksAccessEntryDisabledException
import com.duro.kukie.cluster.exception.EksClusterNotFoundException
import com.duro.kukie.cluster.exception.EksClusterNotReadyException
import com.duro.kukie.cluster.exception.GpuAddonNotInstalledException
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuNodeControlDeniedException
import com.duro.kukie.cluster.exception.GpuNodeGroupNotFoundException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.InvalidNodeCountException
import com.duro.kukie.cluster.exception.ModelChatBusyException
import com.duro.kukie.cluster.exception.ModelChatRejectedException
import com.duro.kukie.cluster.exception.ModelNotReadyException
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.services.eks.model.AccessDeniedException
import software.amazon.awssdk.services.eks.model.AccessScope
import software.amazon.awssdk.services.eks.model.AccessScopeType
import software.amazon.awssdk.services.eks.model.AssociatedAccessPolicy
import software.amazon.awssdk.services.eks.model.AuthenticationMode
import software.amazon.awssdk.services.eks.model.InvalidParameterException
import software.amazon.awssdk.services.eks.model.InvalidRequestException
import software.amazon.awssdk.services.eks.model.Nodegroup
import software.amazon.awssdk.services.eks.model.ResourceInUseException
import software.amazon.awssdk.services.eks.model.ResourceNotFoundException
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.StsException
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

@Component
class AwsEksAccessGateway(
    private val stsClient: StsClient,
    private val clusterAwsProperties: ClusterAwsProperties,
) : EksAccessGateway {

    private val clock: Clock = Clock.systemUTC()
    private val tokenSigner = EksTokenSigner(clock)
    private val sessions = ConcurrentHashMap<AwsTarget, AwsSessionCredentials>()
    private val log = logger()
    private val jsonMapper = JsonMapper.builder().build()
    private val streamExecutor = Executors.newVirtualThreadPerTaskExecutor()

    /** 스택 알림 때 부른다. 역할을 다시 만들었을 수 있어 캐시된 세션을 버리고 새로 맡은 세션으로 바꿔 둔다. */
    override fun verify(target: AwsTarget) {
        sessions.remove(target)
        assume(target)
    }

    override fun roleExists(target: AwsTarget): Boolean {
        sessions.remove(target)
        return try {
            requestSession(target)
            true
        } catch (_: AwsRoleNotAssumableException) {
            false
        }
    }

    override fun listClusters(target: AwsTarget, regions: List<String>): List<EksClusterSummary> = withSession(target) { credentials ->
        regions.flatMap { region ->
            eks(credentials, region).use { eks ->
                translate {
                    eks.listClustersPaginator().clusters().map { name ->
                        val cluster = eks.describeCluster { it.name(name) }.cluster()
                        EksClusterSummary(name = name, region = region, status = cluster.statusAsString(), version = cluster.version())
                    }
                }
            }
        }
    }

    override fun describeCluster(target: AwsTarget, region: String, clusterName: String): EksClusterEndpoint {
        val cluster = withSession(target) { credentials ->
            eks(credentials, region).use { eks -> translate { eks.describeCluster { it.name(clusterName) }.cluster() } }
        }
        if (cluster.accessConfig()?.authenticationMode() == AuthenticationMode.CONFIG_MAP) {
            throw EksAccessEntryDisabledException()
        }
        val endpoint = cluster.endpoint()
        val caData = cluster.certificateAuthority()?.data()
        if (endpoint.isNullOrBlank() || caData.isNullOrBlank()) throw EksClusterNotReadyException()

        return EksClusterEndpoint(endpoint = endpoint.trimEnd('/'), caData = caData)
    }

    override fun registerSelf(target: AwsTarget, region: String, clusterName: String, namespace: String, accessLevel: AccessLevel) {
        val principalArn = roleArn(target)
        withSession(target) { credentials ->
            eks(credentials, region).use { eks ->
                translate { registerSelfOn(eks, clusterName, principalArn, namespace, accessLevel) }
            }
        }
    }

    internal fun registerSelfOn(eks: EksClient, clusterName: String, principalArn: String, namespace: String, accessLevel: AccessLevel) {
        val created = createSelfEntry(eks, clusterName, principalArn)
        try {
            val current = associatedPolicies(eks, clusterName, principalArn)
            when (accessLevel) {
                AccessLevel.FULL -> {
                    val attached = current.any {
                        it.policyArn() == CLUSTER_ADMIN_POLICY_ARN && it.accessScope()?.type() == AccessScopeType.CLUSTER
                    }
                    if (!attached) associate(eks, clusterName, principalArn, AccessLevel.FULL, emptySet())
                }
                AccessLevel.LIMITED -> {
                    val scope = current.firstOrNull { it.policyArn() == EDIT_POLICY_ARN }?.accessScope()
                    if (scope?.type() == AccessScopeType.CLUSTER || namespace in scope?.namespaces().orEmpty()) return
                    // 정책을 다시 붙이면 범위가 바뀌므로 같은 EKS 에 먼저 등록한 namespace 를 함께 넣는다
                    associate(eks, clusterName, principalArn, AccessLevel.LIMITED, scope?.namespaces().orEmpty() + namespace)
                }
            }
        } catch (exception: AccessDeniedException) {
            if (created) discardUnusedEntry(eks, clusterName, principalArn)
            throw exception
        }
    }

    /**
     * 등록에 실패한 entry 는 DB 에 남지 않아 연결 해제가 지우지 못하고, 스택을 다시 만들면 새 역할을 막는 옛 entry 가 된다.
     * 그사이 같은 EKS 의 다른 등록이 정책을 붙였으면 그 등록의 entry 이므로 지우지 않는다.
     */
    private fun discardUnusedEntry(eks: EksClient, clusterName: String, principalArn: String) {
        runCatching {
            if (associatedPolicies(eks, clusterName, principalArn).isEmpty()) deleteEntry(eks, clusterName, principalArn)
        }.onFailure { log.warn("등록에 실패한 access entry 를 지우지 못했습니다: cluster={} reason={}", clusterName, it.javaClass.simpleName) }
    }

    private fun associatedPolicies(eks: EksClient, clusterName: String, principalArn: String): List<AssociatedAccessPolicy> =
        eks.listAssociatedAccessPolicies { it.clusterName(clusterName).principalArn(principalArn) }
            .associatedAccessPolicies()

    override fun selfEntrySettled(target: AwsTarget, region: String, clusterName: String): Boolean {
        val principalArn = roleArn(target)
        return withSession(target) { credentials ->
            eks(credentials, region).use { eks ->
                translate { selfEntrySettledOn(eks, clusterName, principalArn) }
            }
        }
    }

    internal fun selfEntrySettledOn(eks: EksClient, clusterName: String, principalArn: String): Boolean {
        val createdAt = eks.describeAccessEntry { it.clusterName(clusterName).principalArn(principalArn) }.accessEntry()?.createdAt()
            ?: return false
        return Duration.between(createdAt, Instant.now(clock)) >= ENTRY_SETTLE_TIME
    }

    override fun changeAccessLevel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        namespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        val principalArn = roleArn(target)
        withSession(target) { credentials ->
            eks(credentials, region).use { eks ->
                translate { changeAccessLevelOn(eks, clusterName, principalArn, namespaces, accessLevel) }
            }
        }
    }

    /**
     * 새 정책을 먼저 붙이고 옛 정책을 뗀다 — 중간에 실패해도 정책이 하나도 없는 entry 가 되지 않는다.
     * 단 limited 로 내릴 때는 관리자 정책이 남는 쪽이 접속이 끊기는 쪽보다 위험하므로, 다시 해도 끝내 거부되면(등록한 namespace 가
     * AllowedNamespaces 밖이면 그렇다) 정책 없는 entry 가 되더라도 관리자 정책을 떼고 던진다.
     */
    internal fun changeAccessLevelOn(
        eks: EksClient,
        clusterName: String,
        principalArn: String,
        namespaces: Set<String>,
        accessLevel: AccessLevel,
        delay: Duration = ASSOCIATE_DELAY,
    ) {
        require(accessLevel == AccessLevel.FULL || namespaces.isNotEmpty()) { "LIMITED needs namespaces" }
        // 스택 업데이트는 역할 정책을 바꾼 뒤 알린다. 바뀐 정책이 IAM 에 퍼지기 전에는 entry 만들기·붙이기·떼기가 거부되므로 몇 번 다시 한다.
        // 떼기까지 넣는 것은 full → limited 에서 관리자 정책이 남은 채 끝나지 않게 하려는 것이다
        val other = if (accessLevel == AccessLevel.FULL) AccessLevel.LIMITED else AccessLevel.FULL
        var attempt = 1
        while (true) {
            try {
                createSelfEntry(eks, clusterName, principalArn)
                associate(eks, clusterName, principalArn, accessLevel, namespaces)
                detach(eks, clusterName, principalArn, other)
                break
            } catch (exception: AccessDeniedException) {
                if (attempt++ >= ASSOCIATE_ATTEMPTS) {
                    if (accessLevel == AccessLevel.LIMITED) {
                        runCatching { detach(eks, clusterName, principalArn, other) }
                            .onFailure { log.warn("관리자 정책을 떼지 못했습니다: cluster={} reason={}", clusterName, it.javaClass.simpleName) }
                    }
                    throw exception
                }
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw exception
                }
            }
        }
    }

    private fun detach(eks: EksClient, clusterName: String, principalArn: String, accessLevel: AccessLevel) {
        try {
            eks.disassociateAccessPolicy { it.clusterName(clusterName).principalArn(principalArn).policyArn(policyArnOf(accessLevel)) }
        } catch (_: ResourceNotFoundException) {
            // 붙어 있지 않던 정책이다
        }
    }

    private fun associate(
        eks: EksClient,
        clusterName: String,
        principalArn: String,
        accessLevel: AccessLevel,
        namespaces: Collection<String>,
    ) {
        val scope = when (accessLevel) {
            AccessLevel.FULL -> AccessScope.builder().type(AccessScopeType.CLUSTER).build()
            AccessLevel.LIMITED ->
                AccessScope.builder().type(AccessScopeType.NAMESPACE).namespaces(namespaces.distinct().sorted()).build()
        }
        eks.associateAccessPolicy {
            it.clusterName(clusterName).principalArn(principalArn).policyArn(policyArnOf(accessLevel)).accessScope(scope)
        }
    }

    private fun policyArnOf(accessLevel: AccessLevel): String = when (accessLevel) {
        AccessLevel.FULL -> CLUSTER_ADMIN_POLICY_ARN
        AccessLevel.LIMITED -> EDIT_POLICY_ARN
    }

    /**
     * 자기 access entry 를 만든다. 템플릿 v4 에서 고객이 GPU 읽기(GpuRead)를 켰으면 그룹 kukie-gpu-reader 를 달고, 켜지 않았거나
     * v1~v3 역할이면 IAM 이 거부하므로 그룹 없이 다시 만든다. 그룹의 실제 권한(노드·파드 읽기, 고른 namespace 의 파드 프록시)은
     * 고객 클러스터 관리자가 적용한 RBAC 가 정한다(kukie-infra gpu-read-rbac). 그룹은 만들 때만 달 수 있어(템플릿이
     * UpdateAccessEntry 를 주지 않는다) 이미 있는 entry 는 그대로 둔다. 이번 호출이 만들었으면 true.
     */
    internal fun createSelfEntry(eks: EksClient, clusterName: String, principalArn: String): Boolean =
        try {
            try {
                eks.createAccessEntry {
                    it.clusterName(clusterName).principalArn(principalArn).type(STANDARD_ENTRY).kubernetesGroups(GPU_READ_GROUP)
                }
            } catch (_: AccessDeniedException) {
                eks.createAccessEntry { it.clusterName(clusterName).principalArn(principalArn).type(STANDARD_ENTRY) }
            }
            true
        } catch (_: ResourceInUseException) {
            // 이미 만든 access entry 다
            false
        }

    override fun unregisterSelf(
        target: AwsTarget,
        region: String,
        clusterName: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        if (keepNamespaces.isNotEmpty() && accessLevel == AccessLevel.FULL) return
        val principalArn = roleArn(target)
        withSession(target) { credentials ->
            eks(credentials, region).use { eks ->
                translate { unregisterSelfOn(eks, clusterName, principalArn, keepNamespaces, accessLevel) }
            }
        }
    }

    internal fun unregisterSelfOn(
        eks: EksClient,
        clusterName: String,
        principalArn: String,
        keepNamespaces: Set<String>,
        accessLevel: AccessLevel,
    ) {
        if (keepNamespaces.isEmpty()) {
            deleteEntry(eks, clusterName, principalArn)
            return
        }
        if (accessLevel == AccessLevel.FULL) return
        associate(eks, clusterName, principalArn, AccessLevel.LIMITED, keepNamespaces)
        // limited 로 바꾸다 떼지 못한 관리자 정책이 남아 있을 수 있다
        detach(eks, clusterName, principalArn, AccessLevel.FULL)
    }

    private fun deleteEntry(eks: EksClient, clusterName: String, principalArn: String) {
        try {
            eks.deleteAccessEntry { it.clusterName(clusterName).principalArn(principalArn) }
        } catch (_: ResourceNotFoundException) {
            // 이미 지운 entry 다
        }
    }

    override fun namespaceStatus(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        waitForGrant: Boolean,
    ): NamespaceStatus {
        val sslContext = runCatching { trusting(endpoint.caData) }.getOrElse { return unknown("ca", pending = false) }
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .use { http ->
                probeNamespace(attempts = if (waitForGrant) GRANT_ATTEMPTS else 1) {
                    val request = HttpRequest.newBuilder(URI("${endpoint.endpoint}/api/v1/namespaces/$namespace/serviceaccounts/default"))
                        .timeout(PROBE_TIMEOUT)
                        .header("Authorization", "Bearer ${issueToken(target, region, clusterName)}")
                        .GET()
                        .build()
                    http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
                }
            }
    }

    override fun describeSetup(target: AwsTarget, region: String, clusterName: String): EksClusterSetup = withSession(target) { credentials ->
        eks(credentials, region).use { eks ->
            translate {
                val cluster = eks.describeCluster { it.name(clusterName) }.cluster()
                val vpc = cluster.resourcesVpcConfig()
                EksClusterSetup(
                    kubernetesVersion = cluster.version(),
                    platformVersion = cluster.platformVersion(),
                    endpointPublic = vpc?.endpointPublicAccess(),
                    endpointPrivate = vpc?.endpointPrivateAccess(),
                    serviceCidr = cluster.kubernetesNetworkConfig()?.serviceIpv4Cidr(),
                    inventory = unlessV3Denied { inventory(eks, credentials, region, clusterName, vpc?.vpcId()) },
                )
            }
        }
    }

    override fun issueToken(target: AwsTarget, region: String, clusterName: String): String =
        tokenSigner.sign(assume(target), region, clusterName)

    override fun listNodegroupStates(target: AwsTarget, region: String, clusterName: String): List<EksNodegroupState> =
        gpuCall {
            withSession(target) { credentials ->
                eks(credentials, region).use { eks -> translate { nodegroupStatesOn(eks, clusterName) } }
            }
        }

    internal fun nodegroupStatesOn(eks: EksClient, clusterName: String): List<EksNodegroupState> =
        eks.listNodegroupsPaginator { it.clusterName(clusterName) }.nodegroups()
            .mapNotNull { name ->
                // 목록과 상세 조회 사이에 지워진 노드 그룹은 건너뛴다
                ignoringNotFound { eks.describeNodegroup { it.clusterName(clusterName).nodegroupName(name) }.nodegroup() }
                    ?.let { stateOf(name, it) }
            }
            .sortedBy { it.name }

    override fun describeNodegroupState(
        target: AwsTarget,
        region: String,
        clusterName: String,
        nodegroupName: String,
    ): EksNodegroupState? = gpuCall {
        withSession(target) { credentials ->
            eks(credentials, region).use { eks ->
                translate {
                    ignoringNotFound { eks.describeNodegroup { it.clusterName(clusterName).nodegroupName(nodegroupName) }.nodegroup() }
                        ?.let { stateOf(nodegroupName, it) }
                }
            }
        }
    }

    private fun stateOf(name: String, nodegroup: Nodegroup): EksNodegroupState {
        val scaling = nodegroup.scalingConfig()
        return EksNodegroupState(
            name = name,
            instanceTypes = nodegroup.instanceTypes().orEmpty(),
            status = nodegroup.statusAsString(),
            desired = scaling?.desiredSize() ?: 0,
            min = scaling?.minSize() ?: 0,
            max = scaling?.maxSize() ?: 0,
        )
    }

    override fun updateNodegroupDesired(
        target: AwsTarget,
        region: String,
        clusterName: String,
        current: EksNodegroupState,
        desired: Int,
    ) {
        gpuCall {
            withSession(target) { credentials ->
                eks(credentials, region).use { eks -> updateNodegroupDesiredOn(eks, clusterName, current, desired) }
            }
        }
    }

    internal fun updateNodegroupDesiredOn(eks: EksClient, clusterName: String, current: EksNodegroupState, desired: Int) {
        try {
            eks.updateNodegroupConfig { request ->
                request.clusterName(clusterName).nodegroupName(current.name)
                    .scalingConfig { it.minSize(current.min).maxSize(current.max).desiredSize(desired) }
            }
        } catch (_: AccessDeniedException) {
            throw GpuNodeControlDeniedException()
        } catch (_: ResourceInUseException) {
            throw NodeGroupBusyException()
        } catch (_: InvalidRequestException) {
            // 노드 그룹이 지금 상태에서 받을 수 없는 요청이다(만드는 중·지우는 중 등)
            throw NodeGroupBusyException()
        } catch (_: InvalidParameterException) {
            // 읽은 뒤 min·max 가 바뀌었다
            throw InvalidNodeCountException()
        } catch (_: ResourceNotFoundException) {
            throw GpuNodeGroupNotFoundException()
        }
    }

    override fun hasServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
    ): Boolean {
        val response = sendToKubernetes(target, region, clusterName, endpoint, "${servedModelsPath(namespace)}?limit=1", HttpResponse.BodyHandlers.ofString()) { GET() }
        requireServingSuccess(response.statusCode()) { throw GpuAddonNotInstalledException() }
        val items = runCatching { jsonMapper.readTree(response.body()).path("items") }.getOrElse { throw GpuClusterApiFailedException() }
        if (!items.isArray) throw GpuClusterApiFailedException()
        return !items.isEmpty
    }

    override fun createServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        manifest: ServedModelManifest,
    ) {
        val body = jsonMapper.writeValueAsString(servedModelBody(manifest))
        val response = sendToKubernetes(target, region, clusterName, endpoint, servedModelsPath(manifest.namespace), HttpResponse.BodyHandlers.discarding()) {
            header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        }
        if (response.statusCode() == CONFLICT) throw ServedModelExistsException()
        requireServingSuccess(response.statusCode()) { throw GpuAddonNotInstalledException() }
    }

    override fun deleteServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    ) {
        val path = "${servedModelsPath(namespace)}/$name"
        val response = sendToKubernetes(target, region, clusterName, endpoint, path, HttpResponse.BodyHandlers.discarding()) { DELETE() }
        requireServingSuccess(response.statusCode()) { throw ServedModelNotFoundException() }
    }

    override fun getServedModel(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        namespace: String,
        name: String,
    ): ServedModelState {
        val path = "${servedModelsPath(namespace)}/$name"
        val response = sendToKubernetes(target, region, clusterName, endpoint, path, HttpResponse.BodyHandlers.ofString()) { GET() }
        requireServingSuccess(response.statusCode()) { throw ServedModelNotFoundException() }
        val root = try {
            jsonMapper.readTree(response.body())
        } catch (_: JacksonException) {
            throw GpuClusterApiFailedException()
        }
        val metadata = root.path("metadata")
        return ServedModelState(
            model = root.path("spec").path("source").path("huggingface").textOrNull(),
            phase = root.path("status").path("phase").textOrNull(),
            deleting = metadata.path("deletionTimestamp").textOrNull() != null,
            resourceVersion = metadata.path("resourceVersion").textOrNull(),
            keepUntil = metadata.path("annotations").path(KEEP_UNTIL_ANNOTATION).instantOrNull(),
            stopsAt = root.path("status").path("stopsAt").instantOrNull(),
        )
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
        val body = jsonMapper.writeValueAsString(
            mapOf(
                "metadata" to mapOf(
                    "resourceVersion" to resourceVersion,
                    "annotations" to mapOf(KEEP_UNTIL_ANNOTATION to keepUntil.toString()),
                ),
            ),
        )
        val path = "${servedModelsPath(namespace)}/$name"
        val response = sendToKubernetes(target, region, clusterName, endpoint, path, HttpResponse.BodyHandlers.discarding()) {
            header("Content-Type", "application/merge-patch+json").method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        }
        if (response.statusCode() == CONFLICT) return false
        requireServingSuccess(response.statusCode()) { throw ServedModelNotFoundException() }
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
        val sslContext = runCatching { trusting(endpoint.caData) }.getOrElse { throw GpuClusterApiFailedException() }
        val token = gpuCall { issueToken(target, region, clusterName) }
        val http = HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(streamExecutor)
            .build()
        try {
            val request = HttpRequest.newBuilder(URI("${endpoint.endpoint}/api/v1/namespaces/$namespace/services/$name:$SERVING_PORT_NAME/proxy/v1/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val stream = KubernetesModelChatStream(response.body(), http)
            val status = response.statusCode()
            if (status !in SUCCESS) {
                stream.close()
                throw modelChatRefused(status)
            }
            return stream
        } catch (exception: IOException) {
            http.shutdownNow()
            throw kubernetesCallFailed(clusterName, exception)
        } catch (exception: URISyntaxException) {
            http.shutdownNow()
            throw kubernetesCallFailed(clusterName, exception)
        } catch (exception: IllegalArgumentException) {
            http.shutdownNow()
            throw kubernetesCallFailed(clusterName, exception)
        } catch (_: InterruptedException) {
            http.shutdownNow()
            Thread.currentThread().interrupt()
            throw GpuClusterApiFailedException()
        }
    }

    private fun modelChatRefused(status: Int): RuntimeException = when (status) {
        UNAUTHORIZED, FORBIDDEN -> GpuServingDeniedException()
        // 없는 Service, 준비된 파드가 없는 Service, 엔진이 아직 뜨는 중
        NOT_FOUND, SERVICE_UNAVAILABLE -> ModelNotReadyException()
        BAD_REQUEST, CONTENT_TOO_LARGE, UNPROCESSABLE -> ModelChatRejectedException()
        TOO_MANY_REQUESTS -> ModelChatBusyException()
        else -> {
            log.warn("쿠버네티스 API 가 모델 대화를 처리하지 못했습니다: status={}", status)
            GpuClusterApiFailedException()
        }
    }

    /** 상위가 보내는 대로 메모리에 쌓지 않게 줄과 전체 크기를 제한한다. 내용이 지나가므로 예외에 원문을 싣지 않는다. */
    private inner class KubernetesModelChatStream(
        private val body: InputStream,
        private val http: HttpClient,
    ) : ModelChatStream {

        private val input = BufferedInputStream(body)
        private val line = ByteArrayOutputStream()
        private var total = 0L

        override fun readLine(): String? {
            line.reset()
            try {
                while (true) {
                    val byte = input.read()
                    if (byte < 0) return if (line.size() == 0) null else lineText()
                    if (++total > MODEL_CHAT_MAX_BYTES || line.size() >= MODEL_CHAT_MAX_LINE_BYTES) throw GpuClusterApiFailedException()
                    if (byte == LINE_FEED) return lineText()
                    line.write(byte)
                }
            } catch (exception: IOException) {
                // 인터럽트는 브라우저가 끊었거나 시간이 다 된 것이다. 쿠버네티스 쪽 문제가 아니다
                if (!Thread.currentThread().isInterrupted) {
                    log.warn("모델 응답을 끝까지 읽지 못했습니다: reason={}", exception.javaClass.simpleName)
                }
                throw GpuClusterApiFailedException()
            }
        }

        private fun lineText(): String = line.toString(Charsets.UTF_8).removeSuffix("\r")

        override fun close() {
            runCatching { body.close() }
            // close() 는 진행 중인 요청이 끝나기를 기다린다. 끊으려는 것이므로 기다리지 않는다
            http.shutdownNow()
        }
    }

    private fun JsonNode.textOrNull(): String? = takeIf { it.isString }?.asString()

    /** 오븐과 같이 RFC3339 만 읽고, 읽지 못하는 값은 없는 것으로 본다. */
    private fun JsonNode.instantOrNull(): Instant? = textOrNull()?.let {
        try {
            OffsetDateTime.parse(it).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun servedModelsPath(namespace: String): String =
        "/apis/$SERVED_MODEL_API_VERSION/namespaces/$namespace/servedmodels"

    private fun servedModelBody(manifest: ServedModelManifest): Map<String, Any> = mapOf(
        "apiVersion" to SERVED_MODEL_API_VERSION,
        "kind" to "ServedModel",
        "metadata" to mapOf("name" to manifest.name, "namespace" to manifest.namespace),
        "spec" to mapOf(
            "source" to mapOf("huggingface" to manifest.model),
            "engine" to mapOf("image" to manifest.engineImage, "args" to manifest.engineArgs, "gpus" to manifest.gpus),
            "storage" to mapOf("size" to "${manifest.storageGi}Gi", "retainOnDelete" to manifest.retainOnDelete),
        ),
    )

    /** 토큰을 담아 부른다. 토큰과 응답 본문은 로그에 넣지 않는다. */
    private fun <T> sendToKubernetes(
        target: AwsTarget,
        region: String,
        clusterName: String,
        endpoint: EksClusterEndpoint,
        path: String,
        bodyHandler: HttpResponse.BodyHandler<T>,
        configure: HttpRequest.Builder.() -> HttpRequest.Builder,
    ): HttpResponse<T> {
        val sslContext = runCatching { trusting(endpoint.caData) }.getOrElse { throw GpuClusterApiFailedException() }
        val token = gpuCall { issueToken(target, region, clusterName) }
        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .use { http ->
                try {
                    val request = HttpRequest.newBuilder(URI("${endpoint.endpoint}$path"))
                        .timeout(KUBERNETES_WRITE_TIMEOUT)
                        .header("Authorization", "Bearer $token")
                        .configure()
                        .build()
                    http.send(request, bodyHandler)
                } catch (exception: IOException) {
                    throw kubernetesCallFailed(clusterName, exception)
                } catch (exception: URISyntaxException) {
                    throw kubernetesCallFailed(clusterName, exception)
                } catch (exception: IllegalArgumentException) {
                    throw kubernetesCallFailed(clusterName, exception)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw GpuClusterApiFailedException()
                }
            }
    }

    private fun kubernetesCallFailed(clusterName: String, exception: Exception): GpuClusterApiFailedException {
        log.warn("쿠버네티스 API 를 부르지 못했습니다: cluster={} reason={}", clusterName, exception.javaClass.simpleName)
        return GpuClusterApiFailedException()
    }

    /** GPU API 에서 따로 처리하지 않은 AWS 오류(스로틀링, 403 이 아닌 STS 오류, 네트워크)를 500 대신 정해진 오류로 알린다. 메시지는 남기지 않는다. */
    internal fun <T> gpuCall(block: () -> T): T =
        try {
            block()
        } catch (exception: SdkException) {
            log.warn("GPU 요청에서 AWS 를 부르지 못했습니다: reason={}", exception.javaClass.simpleName)
            throw GpuClusterApiFailedException()
        }

    private fun requireServingSuccess(status: Int, onNotFound: () -> Nothing) {
        when (status) {
            in SUCCESS -> return
            UNAUTHORIZED, FORBIDDEN -> throw GpuServingDeniedException()
            NOT_FOUND -> onNotFound()
            else -> {
                log.warn("쿠버네티스 API 가 요청을 처리하지 못했습니다: status={}", status)
                throw GpuClusterApiFailedException()
            }
        }
    }

    /**
     * 캐시된 세션으로 AWS 를 부른다. 고객이 스택을 지우고 다시 만들면 같은 이름의 새 역할이 생겨 옛 세션이 거부되므로
     * (만료도 마찬가지), 세션이 무효라는 오류면 캐시를 버리고 새로 맡아 한 번만 다시 부른다. 그래도 무효면
     * AwsRoleNotAssumableException — 500 대신 역할을 맡을 수 없다고 알린다.
     */
    internal fun <T> withSession(target: AwsTarget, block: (AwsSessionCredentials) -> T): T {
        try {
            return block(assume(target))
        } catch (exception: AwsServiceException) {
            if (!invalidSession(exception)) throw exception
        }
        sessions.remove(target)
        try {
            return block(assume(target))
        } catch (exception: AwsServiceException) {
            if (invalidSession(exception)) throw AwsRoleNotAssumableException()
            throw exception
        }
    }

    /** 맡은 세션 자체가 거부됐는지. 우리 IRSA 자격증명의 STS 오류는 다시 맡아도 같으므로 넣지 않는다. */
    private fun invalidSession(exception: AwsServiceException): Boolean {
        if (exception is StsException) return false
        val code = exception.awsErrorDetails()?.errorCode()
        return code in INVALID_SESSION_CODES ||
            exception.message?.contains(INVALID_TOKEN_MESSAGE, ignoreCase = true) == true
    }

    private fun assume(target: AwsTarget): AwsSessionCredentials {
        val now = Instant.now(clock)
        sessions[target]?.takeIf { it.expirationTime().map { expiry -> expiry.isAfter(now + REUSE_MARGIN) }.orElse(false) }
            ?.let { return it }

        return requestSession(target).also { sessions[target] = it }
    }

    /** STS 에서 새 세션을 받는다. 캐시는 건드리지 않는다. */
    private fun requestSession(target: AwsTarget): AwsSessionCredentials {
        val credentials = try {
            stsClient.assumeRole {
                it.roleArn(roleArn(target))
                    .externalId(target.externalId)
                    .roleSessionName(target.externalId)
                    .durationSeconds(clusterAwsProperties.sessionSeconds)
            }.credentials()
        } catch (exception: StsException) {
            if (exception.statusCode() == FORBIDDEN) throw AwsRoleNotAssumableException()
            throw exception
        }

        return AwsSessionCredentials.builder()
            .accessKeyId(credentials.accessKeyId())
            .secretAccessKey(credentials.secretAccessKey())
            .sessionToken(credentials.sessionToken())
            .expirationTime(credentials.expiration())
            .build()
    }

    private fun inventory(
        eks: EksClient,
        credentials: AwsSessionCredentials,
        region: String,
        clusterName: String,
        vpcId: String?,
    ): EksClusterInventory {
        val subnets = vpcId?.let { subnets(credentials, region, it) }.orEmpty()
        val zoneBySubnet = subnets.associate { it.subnetId() to it.availabilityZone() }

        // 목록과 상세 조회 사이에 지워진 애드온·노드 그룹은 건너뛴다
        val addons = eks.listAddonsPaginator { it.clusterName(clusterName) }.addons().mapNotNull { name ->
            val addon = ignoringNotFound { eks.describeAddon { it.clusterName(clusterName).addonName(name) }.addon() }
                ?: return@mapNotNull null
            EksAddon(name = name, version = addon.addonVersion(), status = addon.statusAsString())
        }
        val nodegroups = eks.listNodegroupsPaginator { it.clusterName(clusterName) }.nodegroups().mapNotNull { name ->
            val nodegroup = ignoringNotFound { eks.describeNodegroup { it.clusterName(clusterName).nodegroupName(name) }.nodegroup() }
                ?: return@mapNotNull null
            val scaling = nodegroup.scalingConfig()
            EksNodegroup(
                name = name,
                instanceTypes = nodegroup.instanceTypes().orEmpty(),
                capacityType = nodegroup.capacityTypeAsString(),
                desired = scaling?.desiredSize(),
                min = scaling?.minSize(),
                max = scaling?.maxSize(),
                zones = nodegroup.subnets().orEmpty().mapNotNull(zoneBySubnet::get).distinct().sorted(),
            )
        }

        return EksClusterInventory(
            addons = addons.sortedBy { it.name },
            nodegroups = nodegroups.sortedBy { it.name },
            subnets = subnets
                .mapNotNull { subnet ->
                    val zone = subnet.availabilityZone() ?: return@mapNotNull null
                    val cidr = subnet.cidrBlock() ?: return@mapNotNull null
                    EksSubnet(zone = zone, cidr = cidr)
                }
                .sortedWith(compareBy({ it.zone }, { it.cidr })),
        )
    }

    private fun subnets(credentials: AwsSessionCredentials, region: String, vpcId: String): List<Subnet> =
        Ec2Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
            .build()
            .use { ec2 ->
                ec2.describeSubnetsPaginator { it.filters(Filter.builder().name("vpc-id").values(vpcId).build()) }
                    .subnets()
                    .toList()
            }

    /** 템플릿 v3 이전 역할은 애드온·노드 그룹·서브넷 읽기 권한이 없다 — 오류 대신 null 로 알린다. */
    private fun <T> unlessV3Denied(block: () -> T): T? =
        try {
            block()
        } catch (_: AccessDeniedException) {
            null
        } catch (exception: Ec2Exception) {
            if (exception.awsErrorDetails()?.errorCode() == EC2_UNAUTHORIZED) null else throw exception
        }

    /** 401·403 은 attempts 까지 다시 묻는다. 한 번이라도 401·403 을 받았으면 뒤의 오류도 반영 중으로 본다. 끝까지 401 뿐이면 인증이 안 된 것이다. */
    internal fun probeNamespace(
        attempts: Int,
        delay: Duration = GRANT_DELAY,
        status: () -> Int,
    ): NamespaceStatus {
        var pending = false
        var authenticated = false
        try {
            repeat(attempts) { attempt ->
                val code = try {
                    status()
                } catch (exception: IOException) {
                    return unknown(exception.javaClass.simpleName, pending)
                } catch (exception: AwsServiceException) {
                    return unknown(exception.javaClass.simpleName, pending)
                }
                when (code) {
                    OK -> return NamespaceStatus.EXISTS
                    NOT_FOUND -> return NamespaceStatus.MISSING
                    UNAUTHORIZED, FORBIDDEN -> {
                        pending = true
                        if (code == FORBIDDEN) authenticated = true
                        if (attempt < attempts - 1) Thread.sleep(delay)
                    }
                    else -> return unknown("status $code", pending)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return unknown("interrupted", pending)
        }
        return if (authenticated) NamespaceStatus.GRANT_PENDING else NamespaceStatus.UNAUTHENTICATED
    }

    /** 확인이 조용히 꺼지면(UNKNOWN 은 등록을 막지 않는다) 왜인지 알 수 없으므로 이유를 남긴다. */
    private fun unknown(reason: String, pending: Boolean): NamespaceStatus {
        log.debug("namespace 를 확인하지 못했습니다: reason={} pending={}", reason, pending)
        return if (pending) NamespaceStatus.GRANT_PENDING else NamespaceStatus.UNKNOWN
    }

    private fun trusting(caData: String): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        CertificateFactory.getInstance("X.509")
            .generateCertificates(Base64.getDecoder().decode(caData).inputStream())
            .forEachIndexed { index, certificate -> trustStore.setCertificateEntry("ca-$index", certificate) }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(trustStore) }
            .trustManagers
        return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }
    }

    private fun <T> ignoringNotFound(block: () -> T): T? =
        try {
            block()
        } catch (_: ResourceNotFoundException) {
            null
        }

    private fun eks(credentials: AwsSessionCredentials, region: String): EksClient =
        EksClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .overrideConfiguration { it.apiCallTimeout(API_CALL_TIMEOUT) }
            .build()

    private fun roleArn(target: AwsTarget): String =
        "arn:aws:iam::${target.accountId}:role/${clusterAwsProperties.roleNamePrefix}${target.externalId}"

    private fun <T> translate(block: () -> T): T =
        try {
            block()
        } catch (_: ResourceNotFoundException) {
            throw EksClusterNotFoundException()
        } catch (_: AccessDeniedException) {
            throw EksAccessDeniedException()
        }

    companion object {
        private const val STANDARD_ENTRY = "STANDARD"
        private const val EDIT_POLICY_ARN = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy"
        private const val CLUSTER_ADMIN_POLICY_ARN = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
        internal const val GPU_READ_GROUP = "kukie-gpu-reader"
        private const val OK = 200
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val CONFLICT = 409
        private const val CONTENT_TOO_LARGE = 413
        private const val UNPROCESSABLE = 422
        private const val TOO_MANY_REQUESTS = 429
        private const val SERVICE_UNAVAILABLE = 503
        private val SUCCESS = 200..299
        private const val SERVED_MODEL_API_VERSION = "serving.kukie.io/v1alpha1"
        private const val KEEP_UNTIL_ANNOTATION = "serving.kukie.io/keep-until"
        private const val SERVING_PORT_NAME = "http"
        private const val LINE_FEED = 10
        private const val MODEL_CHAT_MAX_LINE_BYTES = 1024 * 1024
        private const val MODEL_CHAT_MAX_BYTES = 64L * 1024 * 1024
        private val INVALID_SESSION_CODES = setOf(
            "UnrecognizedClientException",
            "InvalidClientTokenId",
            "ExpiredToken",
            "ExpiredTokenException",
            "AuthFailure",
        )
        private const val INVALID_TOKEN_MESSAGE = "security token included in the request is invalid"
        private const val EC2_UNAUTHORIZED = "UnauthorizedOperation"
        private val API_CALL_TIMEOUT: Duration = Duration.ofSeconds(10)

        // 막 붙인 access policy 가 퍼지기를 잠깐만 기다린다. 더 걸리면 앱이 등록을 다시 부른다
        private const val GRANT_ATTEMPTS = 3
        private val GRANT_DELAY: Duration = Duration.ofSeconds(2)

        // 퍼지는 중인 새 entry 를 옛 entry 로 오해해 지우면 전파가 처음부터 다시 시작되므로 넉넉히 둔다
        private val ENTRY_SETTLE_TIME: Duration = Duration.ofMinutes(5)

        private const val ASSOCIATE_ATTEMPTS = 4
        private val ASSOCIATE_DELAY: Duration = Duration.ofSeconds(3)

        /** 연결마다 부르는 확인이라 응답하지 않는 API 서버가 kubectl 실행을 오래 붙잡지 않게 짧게 둔다. */
        private val PROBE_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val KUBERNETES_WRITE_TIMEOUT: Duration = Duration.ofSeconds(10)

        /** 재사용한 세션으로 만든 토큰도 kubectl 이 끝날 때까지는 살아 있어야 한다. */
        private val REUSE_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
