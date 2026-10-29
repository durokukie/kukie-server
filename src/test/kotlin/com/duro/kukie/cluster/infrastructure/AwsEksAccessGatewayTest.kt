package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.services.eks.model.AccessDeniedException
import software.amazon.awssdk.services.eks.model.AccessScope
import software.amazon.awssdk.services.eks.model.AccessScopeType
import software.amazon.awssdk.services.eks.model.AssociateAccessPolicyRequest
import software.amazon.awssdk.services.eks.model.AssociateAccessPolicyResponse
import software.amazon.awssdk.services.eks.model.AssociatedAccessPolicy
import software.amazon.awssdk.services.eks.model.CreateAccessEntryRequest
import software.amazon.awssdk.services.eks.model.CreateAccessEntryResponse
import software.amazon.awssdk.services.eks.model.DisassociateAccessPolicyRequest
import software.amazon.awssdk.services.eks.model.DisassociateAccessPolicyResponse
import software.amazon.awssdk.services.eks.model.EksException
import software.amazon.awssdk.services.eks.model.ListAssociatedAccessPoliciesRequest
import software.amazon.awssdk.services.eks.model.ListAssociatedAccessPoliciesResponse
import software.amazon.awssdk.services.eks.model.ResourceInUseException
import software.amazon.awssdk.services.eks.model.ResourceNotFoundException
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.services.sts.model.StsException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class AwsEksAccessGatewayTest {

    private val sts = FakeStsClient()
    private val gateway = AwsEksAccessGateway(sts, ClusterAwsProperties())
    private val target = AwsTarget(accountId = "123456789012", externalId = "kukie-0123456789abcdef")

    @Test
    fun `역할 확인은 캐시된 세션을 쓰지 않고 매번 맡아 본다`() {
        // given
        useSession()
        useSession()
        sts.calls shouldBe 1

        // when & then
        gateway.roleExists(target) shouldBe true
        gateway.roleExists(target) shouldBe true
        sts.calls shouldBe 3
    }

    @Test
    fun `역할 확인은 캐시된 세션을 버리고 새 세션을 담지 않는다`() {
        // given
        useSession()

        // when
        gateway.roleExists(target)
        useSession()

        // then — 확인이 캐시를 비웠으니 다음 호출은 다시 맡고, 그 뒤로는 다시 채운 세션을 쓴다
        sts.calls shouldBe 3
        useSession()
        sts.calls shouldBe 3
    }

    @Test
    fun `역할을 맡을 수 없으면(403) 역할이 없다고 본다`() {
        // given
        sts.failure = stsException(403, "AccessDenied")

        // when & then
        gateway.roleExists(target) shouldBe false
    }

    @Test
    fun `그 밖의 STS 오류는 그대로 던진다`() {
        // given
        sts.failure = stsException(400, "Throttling")

        // when & then
        shouldThrow<StsException> { gateway.roleExists(target) }
    }

    @Test
    fun `스택 알림의 확인은 캐시된 세션을 버리고 새로 맡는다`() {
        // given
        gateway.verify(target)

        // when
        gateway.verify(target)

        // then
        sts.calls shouldBe 2
    }

    @Test
    fun `세션이 무효라고 거부되면 캐시를 버리고 새로 맡아 한 번만 다시 부른다`() {
        // given
        gateway.verify(target)
        val sessions = mutableListOf<String>()

        // when
        val result = gateway.withSession(target) { credentials ->
            sessions += credentials.sessionToken()
            if (sessions.size == 1) throw eksException(403, "UnrecognizedClientException", INVALID_TOKEN)
            "ok"
        }

        // then
        result shouldBe "ok"
        sessions shouldBe listOf("session-1", "session-2")
        sts.calls shouldBe 2
    }

    @Test
    fun `EC2 의 AuthFailure 도 세션이 무효한 것으로 본다`() {
        // given
        var attempts = 0

        // when
        gateway.withSession(target) {
            attempts++
            if (attempts == 1) throw ec2Exception("AuthFailure")
        }

        // then
        attempts shouldBe 2
    }

    @Test
    fun `다시 맡아도 세션이 무효면 역할을 맡을 수 없다고 알린다`() {
        // given
        var attempts = 0

        // when & then
        shouldThrow<AwsRoleNotAssumableException> {
            gateway.withSession(target) {
                attempts++
                throw eksException(403, "ExpiredTokenException", "expired")
            }
        }
        attempts shouldBe 2
    }

    @Test
    fun `세션과 상관없는 오류는 다시 부르지 않는다`() {
        // given
        var attempts = 0

        // when & then
        shouldThrow<EksException> {
            gateway.withSession(target) {
                attempts++
                throw eksException(500, "ServerException", "boom")
            }
        }
        attempts shouldBe 1
        sts.calls shouldBe 1
    }

    @Test
    fun `권한이 퍼지기 전의 403 은 다시 묻고 200 이면 namespace 가 있다고 본다`() {
        // given
        val responses = ArrayDeque(listOf(403, 401, 200))

        // when & then
        gateway.probeNamespace(attempts = 5, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.EXISTS
        responses.shouldBeEmpty()
    }

    @Test
    fun `404 면 namespace 가 없다고 본다`() {
        // when & then
        gateway.probeNamespace(attempts = 5, delay = Duration.ZERO) { 404 } shouldBe NamespaceStatus.MISSING
    }

    @Test
    fun `끝내 401·403 만 받으면 권한이 아직 퍼지지 않았다고 본다`() {
        // given
        val responses = ArrayDeque(listOf(401, 403, 403))

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.GRANT_PENDING
        responses.shouldBeEmpty()
    }

    @Test
    fun `401·403 뒤에 오류가 나도 권한이 아직 퍼지지 않았다고 본다`() {
        // given
        val responses = ArrayDeque(listOf(403, 503))
        var calls = 0

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.GRANT_PENDING
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) {
            if (calls++ == 0) 403 else throw IOException("timeout")
        } shouldBe NamespaceStatus.GRANT_PENDING
    }

    @Test
    fun `그 밖의 응답·네트워크 오류면 다시 묻지 않고 모른다고 본다`() {
        // given
        var attempts = 0

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { attempts++; 500 } shouldBe NamespaceStatus.UNKNOWN
        attempts shouldBe 1
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { throw IOException("timeout") } shouldBe NamespaceStatus.UNKNOWN
    }

    @Test
    fun `확인 중 인터럽트되면 모른다고 보고 인터럽트 표시를 남긴다`() {
        // when
        val result = gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { throw InterruptedException() }

        // then
        result shouldBe NamespaceStatus.UNKNOWN
        Thread.interrupted() shouldBe true
    }

    @Test
    fun `GPU 읽기를 켠 역할이면 그룹 kukie-gpu-reader 를 달아 자기 entry 를 만든다`() {
        // given
        val eks = FakeEksClient(denyGroups = false)

        // when
        gateway.createSelfEntry(eks, "kukie-gpu", PRINCIPAL)

        // then
        eks.requests.map { it.kubernetesGroups() } shouldBe listOf(listOf("kukie-gpu-reader"))
        eks.requests.single().principalArn() shouldBe PRINCIPAL
        eks.requests.single().type() shouldBe "STANDARD"
        eks.requests.single().username() shouldBe null
    }

    @Test
    fun `그룹이 거부되면(GpuRead 끔·v1~v3) 그룹 없이 다시 만든다`() {
        // given
        val eks = FakeEksClient(denyGroups = true)

        // when
        gateway.createSelfEntry(eks, "kukie-gpu", PRINCIPAL)

        // then
        eks.requests.map { it.kubernetesGroups() } shouldBe listOf(listOf("kukie-gpu-reader"), emptyList())
    }

    @Test
    fun `이미 entry 가 있으면 그대로 둔다`() {
        // given — 그룹 시도는 거부되고, 그룹 없는 시도는 이미 있다고 답한다
        val eks = FakeEksClient(denyGroups = true, exists = true)

        // when & then (던지지 않는다)
        gateway.createSelfEntry(eks, "kukie-gpu", PRINCIPAL)
        eks.requests.size shouldBe 2
        FakeEksClient(denyGroups = false, exists = true).also { gateway.createSelfEntry(it, "kukie-gpu", PRINCIPAL) }.requests.size shouldBe 1
    }

    @Test
    fun `그룹 없이도 거부되면 그대로 던진다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, denyAll = true)

        // when & then
        shouldThrow<AccessDeniedException> { gateway.createSelfEntry(eks, "kukie-gpu", PRINCIPAL) }
        eks.requests.size shouldBe 2
    }

    @Test
    fun `전체 권한이면 클러스터 관리자 정책을 클러스터 전체 범위로 붙인다`() {
        // given
        val eks = FakeEksClient(denyGroups = true)

        // when
        gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "kukie-demo", AccessLevel.FULL)

        // then
        val request = eks.associations.single()
        request.policyArn() shouldBe CLUSTER_ADMIN_POLICY
        request.accessScope().type() shouldBe AccessScopeType.CLUSTER
        request.accessScope().namespaces().shouldBeEmpty()
    }

    @Test
    fun `전체 권한이 이미 붙어 있으면 다시 붙이지 않는다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, associated = listOf(associated(CLUSTER_ADMIN_POLICY, clusterScope())))

        // when
        gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "kukie-app", AccessLevel.FULL)

        // then
        eks.associations.shouldBeEmpty()
    }

    @Test
    fun `제한된 권한이면 먼저 등록한 namespace 와 함께 Edit 정책을 namespace 범위로 붙인다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, associated = listOf(associated(EDIT_POLICY, namespaceScope("kukie-demo"))))

        // when
        gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "kukie-app", AccessLevel.LIMITED)

        // then
        val request = eks.associations.single()
        request.policyArn() shouldBe EDIT_POLICY
        request.accessScope().type() shouldBe AccessScopeType.NAMESPACE
        request.accessScope().namespaces() shouldBe listOf("kukie-app", "kukie-demo")
    }

    @Test
    fun `전체 권한으로 바꾸면 클러스터 관리자 정책을 먼저 붙이고 Edit 정책을 뗀다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true)

        // when
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ZERO)

        // then
        eks.policyCalls shouldBe listOf("associate" to CLUSTER_ADMIN_POLICY, "disassociate" to EDIT_POLICY)
        eks.associations.single().accessScope().type() shouldBe AccessScopeType.CLUSTER
    }

    @Test
    fun `제한된 권한으로 바꾸면 등록한 namespace 범위로 Edit 정책을 붙이고 클러스터 관리자 정책을 뗀다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, detachedAlready = true)

        // when — 떼려는 정책이 이미 없어도 던지지 않는다
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo", "kukie-app"), AccessLevel.LIMITED, Duration.ZERO)

        // then
        eks.policyCalls shouldBe listOf("associate" to EDIT_POLICY, "disassociate" to CLUSTER_ADMIN_POLICY)
        eks.associations.single().accessScope().run {
            type() shouldBe AccessScopeType.NAMESPACE
            namespaces() shouldBe listOf("kukie-app", "kukie-demo")
        }
    }

    @Test
    fun `바뀐 역할 정책이 퍼지기 전에 거부되면 다시 붙인다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = 2)

        // when
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ZERO)

        // then
        eks.policyCalls shouldBe listOf(
            "associate" to CLUSTER_ADMIN_POLICY,
            "associate" to CLUSTER_ADMIN_POLICY,
            "associate" to CLUSTER_ADMIN_POLICY,
            "disassociate" to EDIT_POLICY,
        )
    }

    @Test
    fun `바뀐 역할 정책이 퍼지기 전에 entry 만들기가 거부되어도 다시 한다`() {
        // given — v5 역할은 그룹 달기를 늘 거부하고, 그룹 없는 만들기도 처음 두 번은 거부된다
        val eks = FakeEksClient(denyGroups = true, deniedEntries = 2)

        // when
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ZERO)

        // then
        eks.requests.size shouldBe 6
        eks.policyCalls shouldBe listOf("associate" to CLUSTER_ADMIN_POLICY, "disassociate" to EDIT_POLICY)
    }

    @Test
    fun `다시 하려고 기다리다 인터럽트되면 옛 정책을 떼지 않고 던지며 인터럽트 표시를 남긴다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = 1)
        Thread.currentThread().interrupt()

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ofSeconds(1))
        }
        Thread.interrupted() shouldBe true
        eks.policyCalls shouldBe listOf("associate" to CLUSTER_ADMIN_POLICY)
    }

    @Test
    fun `끝내 붙이지 못하면 옛 정책을 떼지 않고 던진다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ZERO)
        }
        eks.policyCalls.map { it.first }.distinct() shouldBe listOf("associate")
    }

    /** EKS 를 부르는 메서드처럼 캐시된 세션을 쓴다. */
    private fun useSession() = gateway.withSession(target) { }

    private fun eksException(status: Int, code: String, message: String): EksException =
        EksException.builder()
            .statusCode(status)
            .message(message)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(message).build())
            .build() as EksException

    private fun ec2Exception(code: String): Ec2Exception =
        Ec2Exception.builder()
            .statusCode(401)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .build() as Ec2Exception

    private fun stsException(status: Int, code: String): StsException =
        StsException.builder()
            .statusCode(status)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .build() as StsException

    private fun associated(policyArn: String, scope: AccessScope): AssociatedAccessPolicy =
        AssociatedAccessPolicy.builder().policyArn(policyArn).accessScope(scope).build()

    private fun clusterScope(): AccessScope = AccessScope.builder().type(AccessScopeType.CLUSTER).build()

    private fun namespaceScope(vararg namespaces: String): AccessScope =
        AccessScope.builder().type(AccessScopeType.NAMESPACE).namespaces(*namespaces).build()

    companion object {
        private const val EDIT_POLICY = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy"
        private const val CLUSTER_ADMIN_POLICY = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
        private const val INVALID_TOKEN = "The security token included in the request is invalid."
        private const val PRINCIPAL = "arn:aws:iam::123456789012:role/KukieAccess-kukie-0123456789abcdef"
    }

    /**
     * 고객 역할의 IAM 판단만 흉내 낸다: 그룹을 단 요청은 denyGroups 면 거부, 모든 요청은 denyAll 이면 거부.
     * 그룹 없는 entry 만들기는 처음 deniedEntries 번, 정책 붙이기는 처음 deniedAssociations 번 거부하고,
     * detachedAlready 면 떼기가 붙어 있지 않다고 답한다.
     */
    private class FakeEksClient(
        private val denyGroups: Boolean,
        private val denyAll: Boolean = false,
        private val exists: Boolean = false,
        private var deniedEntries: Int = 0,
        private val associated: List<AssociatedAccessPolicy> = emptyList(),
        private var deniedAssociations: Int = 0,
        private val detachedAlready: Boolean = false,
    ) : EksClient {
        val requests = mutableListOf<CreateAccessEntryRequest>()
        val associations = mutableListOf<AssociateAccessPolicyRequest>()
        val policyCalls = mutableListOf<Pair<String, String>>()

        override fun listAssociatedAccessPolicies(request: ListAssociatedAccessPoliciesRequest): ListAssociatedAccessPoliciesResponse =
            ListAssociatedAccessPoliciesResponse.builder().associatedAccessPolicies(associated).build()

        override fun associateAccessPolicy(request: AssociateAccessPolicyRequest): AssociateAccessPolicyResponse {
            policyCalls += "associate" to request.policyArn()
            if (deniedAssociations > 0) {
                deniedAssociations--
                throw AccessDeniedException.builder().statusCode(403).message("not authorized").build()
            }
            associations += request
            return AssociateAccessPolicyResponse.builder().build()
        }

        override fun disassociateAccessPolicy(request: DisassociateAccessPolicyRequest): DisassociateAccessPolicyResponse {
            policyCalls += "disassociate" to request.policyArn()
            if (detachedAlready) throw ResourceNotFoundException.builder().statusCode(404).message("not associated").build()
            return DisassociateAccessPolicyResponse.builder().build()
        }

        override fun createAccessEntry(request: CreateAccessEntryRequest): CreateAccessEntryResponse {
            requests += request
            if (denyAll || (denyGroups && request.hasKubernetesGroups() && request.kubernetesGroups().isNotEmpty())) {
                throw AccessDeniedException.builder().statusCode(403).message("not authorized").build()
            }
            if (deniedEntries > 0) {
                deniedEntries--
                throw AccessDeniedException.builder().statusCode(403).message("not authorized").build()
            }
            if (exists) throw ResourceInUseException.builder().statusCode(409).message("exists").build()
            return CreateAccessEntryResponse.builder().build()
        }

        override fun serviceName(): String = "eks"

        override fun close() = Unit
    }

    private class FakeStsClient : StsClient {
        var calls = 0
        var failure: StsException? = null

        override fun assumeRole(request: AssumeRoleRequest): AssumeRoleResponse {
            calls++
            failure?.let { throw it }
            return AssumeRoleResponse.builder()
                .credentials(
                    Credentials.builder()
                        .accessKeyId("AKIDEXAMPLE")
                        .secretAccessKey("secret-key")
                        .sessionToken("session-$calls")
                        .expiration(Instant.now().plus(1, ChronoUnit.HOURS))
                        .build(),
                )
                .build()
        }

        override fun serviceName(): String = "sts"

        override fun close() = Unit
    }
}
