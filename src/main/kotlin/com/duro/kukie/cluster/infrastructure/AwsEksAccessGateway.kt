package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksAddon
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.EksClusterInventory
import com.duro.kukie.cluster.application.port.out.EksClusterSetup
import com.duro.kukie.cluster.application.port.out.EksClusterSummary
import com.duro.kukie.cluster.application.port.out.EksNodegroup
import com.duro.kukie.cluster.application.port.out.EksSubnet
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.EksAccessEntryDisabledException
import com.duro.kukie.cluster.exception.EksClusterNotFoundException
import com.duro.kukie.cluster.exception.EksClusterNotReadyException
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.services.eks.model.AccessDeniedException
import software.amazon.awssdk.services.eks.model.AccessScope
import software.amazon.awssdk.services.eks.model.AccessScopeType
import software.amazon.awssdk.services.eks.model.AuthenticationMode
import software.amazon.awssdk.services.eks.model.ResourceInUseException
import software.amazon.awssdk.services.eks.model.ResourceNotFoundException
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.StsException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
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
        createSelfEntry(eks, clusterName, principalArn)
        val current = eks.listAssociatedAccessPolicies { it.clusterName(clusterName).principalArn(principalArn) }
            .associatedAccessPolicies()
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

    /** 새 정책을 먼저 붙이고 옛 정책을 뗀다 — 중간에 실패해도 정책이 하나도 없는 entry 가 되지 않는다. */
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
                if (attempt++ >= ASSOCIATE_ATTEMPTS) throw exception
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
     * UpdateAccessEntry 를 주지 않는다) 이미 있는 entry 는 그대로 둔다.
     */
    internal fun createSelfEntry(eks: EksClient, clusterName: String, principalArn: String) {
        try {
            try {
                eks.createAccessEntry {
                    it.clusterName(clusterName).principalArn(principalArn).type(STANDARD_ENTRY).kubernetesGroups(GPU_READ_GROUP)
                }
            } catch (_: AccessDeniedException) {
                eks.createAccessEntry { it.clusterName(clusterName).principalArn(principalArn).type(STANDARD_ENTRY) }
            }
        } catch (_: ResourceInUseException) {
            // 이미 만든 access entry 다
        }
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
            try {
                eks.deleteAccessEntry { it.clusterName(clusterName).principalArn(principalArn) }
            } catch (_: ResourceNotFoundException) {
                // 이미 지운 entry 다
            }
            return
        }
        if (accessLevel == AccessLevel.FULL) return
        associate(eks, clusterName, principalArn, AccessLevel.LIMITED, keepNamespaces)
        // limited 로 바꾸다 떼지 못한 관리자 정책이 남아 있을 수 있다
        detach(eks, clusterName, principalArn, AccessLevel.FULL)
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

    /** 401·403 은 attempts 까지 다시 묻는다. 한 번이라도 401·403 을 받았으면 뒤의 오류도 반영 중으로 본다. */
    internal fun probeNamespace(
        attempts: Int,
        delay: Duration = GRANT_DELAY,
        status: () -> Int,
    ): NamespaceStatus {
        var pending = false
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
                        if (attempt < attempts - 1) Thread.sleep(delay)
                    }
                    else -> return unknown("status $code", pending)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return unknown("interrupted", pending)
        }
        return NamespaceStatus.GRANT_PENDING
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
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
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

        private const val ASSOCIATE_ATTEMPTS = 4
        private val ASSOCIATE_DELAY: Duration = Duration.ofSeconds(3)

        /** 연결마다 부르는 확인이라 응답하지 않는 API 서버가 kubectl 실행을 오래 붙잡지 않게 짧게 둔다. */
        private val PROBE_TIMEOUT: Duration = Duration.ofSeconds(3)

        /** 재사용한 세션으로 만든 토큰도 kubectl 이 끝날 때까지는 살아 있어야 한다. */
        private val REUSE_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
