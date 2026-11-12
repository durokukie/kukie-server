package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.EksClusterEndpoint
import com.duro.kukie.cluster.application.port.out.PrecheckJobManifest
import com.duro.kukie.cluster.application.port.out.PrecheckPodState
import com.duro.kukie.cluster.application.port.out.EksNodegroupState
import com.duro.kukie.cluster.application.port.out.EngineResources
import com.duro.kukie.cluster.application.port.out.NamespaceStatus
import com.duro.kukie.cluster.application.port.out.ServedModelManifest
import com.duro.kukie.cluster.application.port.out.ServedModelState
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.exception.AwsRoleNotAssumableException
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.services.eks.model.AccessDeniedException
import software.amazon.awssdk.services.eks.model.AccessEntry
import software.amazon.awssdk.services.eks.model.AccessScope
import software.amazon.awssdk.services.eks.model.AccessScopeType
import software.amazon.awssdk.services.eks.model.AssociateAccessPolicyRequest
import software.amazon.awssdk.services.eks.model.AssociateAccessPolicyResponse
import software.amazon.awssdk.services.eks.model.AssociatedAccessPolicy
import software.amazon.awssdk.services.eks.model.CreateAccessEntryRequest
import software.amazon.awssdk.services.eks.model.CreateAccessEntryResponse
import software.amazon.awssdk.services.eks.model.DeleteAccessEntryRequest
import software.amazon.awssdk.services.eks.model.DeleteAccessEntryResponse
import software.amazon.awssdk.services.eks.model.DescribeAccessEntryRequest
import software.amazon.awssdk.services.eks.model.DescribeAccessEntryResponse
import software.amazon.awssdk.services.eks.model.DisassociateAccessPolicyRequest
import software.amazon.awssdk.services.eks.model.DisassociateAccessPolicyResponse
import software.amazon.awssdk.services.eks.model.DescribeNodegroupRequest
import software.amazon.awssdk.services.eks.model.DescribeNodegroupResponse
import software.amazon.awssdk.services.eks.model.EksException
import software.amazon.awssdk.services.eks.model.InvalidParameterException
import software.amazon.awssdk.services.eks.model.InvalidRequestException
import software.amazon.awssdk.services.eks.model.ListNodegroupsRequest
import software.amazon.awssdk.services.eks.model.ListNodegroupsResponse
import software.amazon.awssdk.services.eks.model.Nodegroup
import software.amazon.awssdk.services.eks.model.NodegroupScalingConfig
import software.amazon.awssdk.services.eks.model.ListAssociatedAccessPoliciesRequest
import software.amazon.awssdk.services.eks.model.ListAssociatedAccessPoliciesResponse
import software.amazon.awssdk.services.eks.model.ResourceInUseException
import software.amazon.awssdk.services.eks.model.ResourceNotFoundException
import software.amazon.awssdk.services.eks.model.UpdateNodegroupConfigRequest
import software.amazon.awssdk.services.eks.model.UpdateNodegroupConfigResponse
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.amazon.awssdk.services.sts.model.Credentials
import software.amazon.awssdk.services.sts.model.StsException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class AwsEksAccessGatewayTest {

    private val sts = FakeStsClient()
    private val gateway = AwsEksAccessGateway(sts, ClusterAwsProperties())
    private val target = AwsTarget(accountId = "123456789012", externalId = "kukie-0123456789abcdef")
    private var kubernetes: HttpServer? = null

    @AfterEach
    fun stopKubernetes() {
        kubernetes?.stop(0)
    }

    @Test
    fun `노드 그룹의 상태와 크기를 이름 순으로 읽고 사이에 지워진 그룹은 건너뛴다`() {
        // given
        val eks = NodegroupEksClient(
            names = listOf("gpu", "gone", "cpu"),
            nodegroups = mapOf("gpu" to nodegroup("g6e.xlarge"), "cpu" to nodegroup("t3.large")),
        )

        // when
        val states = gateway.nodegroupStatesOn(eks, "demo")

        // then
        states.map { it.name } shouldBe listOf("cpu", "gpu")
        states[1].run {
            instanceTypes shouldBe listOf("g6e.xlarge")
            status shouldBe "ACTIVE"
            desired shouldBe 1
            min shouldBe 0
            max shouldBe 2
            gpu shouldBe true
        }
        states[0].gpu shouldBe false
    }

    @Test
    fun `노드 수를 바꿀 때 읽어 둔 min·max 를 desired 와 함께 보낸다`() {
        // given
        val eks = NodegroupEksClient()

        // when
        gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2)

        // then
        eks.updates.single().run {
            clusterName() shouldBe "demo"
            nodegroupName() shouldBe "gpu"
            scalingConfig().desiredSize() shouldBe 2
            scalingConfig().minSize() shouldBe 1
            scalingConfig().maxSize() shouldBe 3
        }
    }

    @Test
    fun `AWS 가 노드 수 변경을 거부하면 권한 없음으로 바꾼다`() {
        val eks = NodegroupEksClient(updateFailure = AccessDeniedException.builder().statusCode(403).message("denied").build())

        shouldThrow<GpuNodeControlDeniedException> { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `다른 변경이 진행 중이면 바쁘다고 바꾼다`() {
        val eks = NodegroupEksClient(updateFailure = ResourceInUseException.builder().statusCode(409).message("busy").build())

        shouldThrow<NodeGroupBusyException> { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `노드 그룹이 받을 수 없는 상태면 바쁘다고 바꾼다`() {
        val eks = NodegroupEksClient(updateFailure = InvalidRequestException.builder().statusCode(400).message("state").build())

        shouldThrow<NodeGroupBusyException> { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `AWS 가 범위를 벗어났다고 하면 잘못된 노드 수로 바꾼다`() {
        val eks = NodegroupEksClient(updateFailure = InvalidParameterException.builder().statusCode(400).message("range").build())

        shouldThrow<InvalidNodeCountException> { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `바꾸는 사이 노드 그룹이 사라지면 찾을 수 없다고 바꾼다`() {
        val eks = NodegroupEksClient(updateFailure = ResourceNotFoundException.builder().statusCode(404).message("gone").build())

        shouldThrow<GpuNodeGroupNotFoundException> { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `따로 처리하지 않은 EKS 오류면 클러스터 실패로 알린다`() {
        // given
        val eks = NodegroupEksClient(updateFailure = eksException(500, "ServerException", "server"))

        // when & then
        shouldThrow<GpuClusterApiFailedException> {
            gateway.gpuCall { gateway.updateNodegroupDesiredOn(eks, "demo", GPU_NODE_GROUP, 2) }
        }
    }

    @Test
    fun `AWS 를 부르다 네트워크 오류가 나면 클러스터 실패로 알린다`() {
        shouldThrow<GpuClusterApiFailedException> {
            gateway.gpuCall { throw SdkClientException.create("timeout") }
        }
    }

    @Test
    fun `세션이 무효한 오류는 여전히 역할을 맡을 수 없다고 알린다`() {
        shouldThrow<AwsRoleNotAssumableException> {
            gateway.gpuCall {
                gateway.withSession(target) { throw eksException(403, "UnrecognizedClientException", INVALID_TOKEN) }
            }
        }
    }

    @Test
    fun `STS 가 스로틀링하면 노드 그룹 호출이 클러스터 실패로 알린다`() {
        // given
        sts.failure = stsException(400, "Throttling")

        // when & then
        shouldThrow<GpuClusterApiFailedException> { gateway.listNodegroupStates(target, "ap-northeast-2", "demo") }
        shouldThrow<GpuClusterApiFailedException> { gateway.describeNodegroupState(target, "ap-northeast-2", "demo", "gpu") }
        shouldThrow<GpuClusterApiFailedException> { gateway.updateNodegroupDesired(target, "ap-northeast-2", "demo", GPU_NODE_GROUP, 2) }
    }

    @Test
    fun `STS 가 거부하면 노드 그룹 호출이 역할을 맡을 수 없다고 알린다`() {
        // given
        sts.failure = stsException(403, "AccessDenied")

        // when & then
        shouldThrow<AwsRoleNotAssumableException> { gateway.listNodegroupStates(target, "ap-northeast-2", "demo") }
    }

    @Test
    fun `ServedModel 의 모델과 상태와 멈추는 시각을 읽는다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(
            200,
            """
            {"metadata":{"resourceVersion":"42","annotations":{"serving.kukie.io/keep-until":"2026-11-05T00:00:00+09:00"}},
             "spec":{"source":{"huggingface":"openai/gpt-oss-20b"}},
             "status":{"phase":"Ready","stopsAt":"2026-11-04T12:00:00Z"}}
            """.trimIndent(),
            calls,
        )

        // when
        val state = gateway.getServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")

        // then
        calls.single().run {
            method shouldBe "GET"
            path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels/gpt-oss-20b"
        }
        state shouldBe ServedModelState(
            model = "openai/gpt-oss-20b",
            phase = "Ready",
            deleting = false,
            resourceVersion = "42",
            keepUntil = Instant.parse("2026-11-04T15:00:00Z"),
            stopsAt = Instant.parse("2026-11-04T12:00:00Z"),
        )
    }

    @Test
    fun `지우는 중인 표시를 읽고 읽지 못하는 시각과 없는 값은 null 이다`() {
        // given
        val endpoint = kubernetesAnswering(
            200,
            """{"metadata":{"deletionTimestamp":"2026-11-04T12:00:00Z","annotations":{"serving.kukie.io/keep-until":"tomorrow"}}}""",
        )

        // when
        val state = gateway.getServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")

        // then
        state shouldBe ServedModelState(null, null, true, null, null, null)
    }

    @Test
    fun `ServedModel 을 읽을 때 쿠버네티스 응답을 오류로 옮긴다`() {
        // when & then
        shouldThrow<ServedModelNotFoundException> { readServedModel(kubernetesAnswering(404)) }
        kubernetes?.stop(0)
        shouldThrow<GpuServingDeniedException> { readServedModel(kubernetesAnswering(403)) }
        kubernetes?.stop(0)
        shouldThrow<GpuClusterApiFailedException> { readServedModel(kubernetesAnswering(500)) }
        kubernetes?.stop(0)
        shouldThrow<GpuClusterApiFailedException> { readServedModel(kubernetesAnswering(200, "not json")) }
    }

    @Test
    fun `keep-until 을 읽은 버전과 함께 merge patch 로 쓴다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, calls = calls)

        // when
        val written = gateway.keepServedModelUntil(
            target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", Instant.parse("2026-11-04T15:00:00Z"), "42",
        )

        // then
        written shouldBe true
        calls.single().run {
            method shouldBe "PATCH"
            path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels/gpt-oss-20b"
            contentType shouldBe "application/merge-patch+json"
            body shouldBe """{"metadata":{"resourceVersion":"42","annotations":{"serving.kukie.io/keep-until":"2026-11-04T15:00:00Z"}}}"""
        }
    }

    @Test
    fun `쓰는 사이 ServedModel 이 바뀌었으면 false 다`() {
        // given
        val endpoint = kubernetesAnswering(409)

        // when
        val written = gateway.keepServedModelUntil(
            target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", Instant.parse("2026-11-04T15:00:00Z"), "42",
        )

        // then
        written shouldBe false
    }

    @Test
    fun `측정 요청은 measure annotation 만 읽은 버전과 함께 merge patch 로 쓴다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, calls = calls)

        // when
        val written = gateway.requestServedModelMeasure(
            target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", "m-1793000000", "42",
        )

        // then
        written shouldBe true
        calls.single().run {
            method shouldBe "PATCH"
            path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels/gpt-oss-20b"
            contentType shouldBe "application/merge-patch+json"
            body shouldBe """{"metadata":{"resourceVersion":"42","annotations":{"serving.kukie.io/measure":"m-1793000000"}}}"""
        }
    }

    @Test
    fun `사전 검사 Job 은 권한을 줄이고 시간 상한을 두고 만든다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(201, "", calls)

        // when
        gateway.createPrecheckJob(
            target, "ap-northeast-2", "demo", endpoint,
            PrecheckJobManifest("default", "kukie-gpu-precheck-1", "curl@sha256:a", "true"),
        )

        // then
        calls.single().run {
            method shouldBe "POST"
            path shouldBe "/apis/batch/v1/namespaces/default/jobs"
            body.contains(""""automountServiceAccountToken":false""") shouldBe true
            body.contains(""""activeDeadlineSeconds":120""") shouldBe true
            body.contains(""""ttlSecondsAfterFinished":600""") shouldBe true
            body.contains(""""allowPrivilegeEscalation":false""") shouldBe true
        }
    }

    @Test
    fun `사전 검사 Pod 의 종료 메시지와 대기 이유를 읽고 없으면 null 이다`() {
        // given
        val done = kubernetesAnswering(
            200,
            """{"items":[{"status":{"phase":"Succeeded","containerStatuses":[{"state":{"terminated":{"message":"{}"}}}]}}]}""",
        )
        fun read(endpoint: EksClusterEndpoint) =
            gateway.getPrecheckPod(target, "ap-northeast-2", "demo", endpoint, "default", "kukie-gpu-precheck-1")

        // when & then
        read(done) shouldBe PrecheckPodState("Succeeded", null, false, "{}")
        kubernetes?.stop(0)
        read(
            kubernetesAnswering(
                200,
                """{"items":[{"status":{"phase":"Pending","containerStatuses":[{"state":{"waiting":{"reason":"ErrImagePull"}}}]}}]}""",
            ),
        ) shouldBe PrecheckPodState("Pending", "ErrImagePull", false, null)
    }

    @Test
    fun `사전 검사 Pod 가 없으면 Job 으로 도는 중과 실패와 지워짐을 나눈다`() {
        // given
        fun read(endpoint: EksClusterEndpoint) =
            gateway.getPrecheckPod(target, "ap-northeast-2", "demo", endpoint, "default", "kukie-gpu-precheck-1")
        fun answering(job: Pair<Int, String>): EksClusterEndpoint {
            kubernetes?.stop(0)
            return kubernetesRouting { path -> if (path.contains("/pods")) 200 to """{"items":[]}""" else job }
        }

        // when & then
        read(answering(200 to """{"status":{}}""")) shouldBe PrecheckPodState("Pending", null, false, null)
        read(answering(200 to """{"status":{"conditions":[{"type":"Failed","status":"True"}]}}""")) shouldBe
            PrecheckPodState("Failed", null, false, null)
        read(answering(200 to """{"status":{"conditions":[{"type":"Complete","status":"True"}]}}""")) shouldBe null
        read(answering(404 to "")) shouldBe null
    }

    @Test
    fun `측정 요청을 쓰는 사이 ServedModel 이 바뀌었으면 false 다`() {
        // given
        val endpoint = kubernetesAnswering(409)

        // when
        val written = gateway.requestServedModelMeasure(
            target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", "m-1793000000", "42",
        )

        // then
        written shouldBe false
    }

    @Test
    fun `측정 요청이 쿠버네티스에 거부되거나 모델이 없으면 그대로 알린다`() {
        // given
        fun request(endpoint: EksClusterEndpoint) = gateway.requestServedModelMeasure(
            target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", "m-1793000000", "42",
        )

        // when & then
        shouldThrow<ServedModelNotFoundException> { request(kubernetesAnswering(404)) }
        kubernetes?.stop(0)
        shouldThrow<GpuServingDeniedException> { request(kubernetesAnswering(403)) }
    }

    @Test
    fun `모델 대화를 Service 프록시로 보내고 응답을 줄 단위로 읽는다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, "data: 첫 줄\r\n\ndata: [DONE]", calls)

        // when
        val lines = openChat(endpoint).use { stream -> generateSequence { stream.readLine() }.toList() }

        // then
        lines shouldBe listOf("data: 첫 줄", "", "data: [DONE]")
        calls.single().run {
            method shouldBe "POST"
            path shouldBe "/api/v1/namespaces/llm/services/gpt-oss-20b:http/proxy/v1/chat/completions"
            authorization!!.startsWith("Bearer k8s-aws-v1.") shouldBe true
            contentType shouldBe "application/json"
            body shouldBe """{"model":"m"}"""
        }
    }

    @Test
    fun `모델 대화의 실패 응답을 오류로 옮기고 본문을 싣지 않는다`() {
        // given
        val cases = mapOf<Int, Class<out RuntimeException>>(
            401 to GpuServingDeniedException::class.java,
            403 to GpuServingDeniedException::class.java,
            404 to ModelNotReadyException::class.java,
            503 to ModelNotReadyException::class.java,
            400 to ModelChatRejectedException::class.java,
            413 to ModelChatRejectedException::class.java,
            422 to ModelChatRejectedException::class.java,
            429 to ModelChatBusyException::class.java,
            500 to GpuClusterApiFailedException::class.java,
        )

        cases.forEach { (status, expected) ->
            val endpoint = kubernetesAnswering(status, "사용자 메시지 원문")

            // when
            val failure = runCatching { openChat(endpoint) }.exceptionOrNull()!!

            // then
            failure.javaClass shouldBe expected
            failure.message!!.contains("사용자 메시지 원문") shouldBe false
            kubernetes?.stop(0)
        }
    }

    @Test
    fun `모델 대화에서 토큰을 만들지 못하면 쿠버네티스를 부르지 않는다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, calls = calls)
        sts.failure = stsException(500, "ServiceUnavailable")

        // when & then
        shouldThrow<GpuClusterApiFailedException> { openChat(endpoint) }
        calls.shouldBeEmpty()
    }

    @Test
    fun `모델 응답의 한 줄이 상한을 넘으면 메모리에 쌓지 않고 끊는다`() {
        // given
        val endpoint = kubernetesAnswering(200, "x".repeat(1024 * 1024 + 1))

        // when & then
        openChat(endpoint).use { stream -> shouldThrow<GpuClusterApiFailedException> { stream.readLine() } }
    }

    @Test
    fun `모델 응답을 기다리다 인터럽트되면 실패로 끝나고 인터럽트 표시를 남긴다`() {
        // given
        val endpoint = kubernetesAnswering(200, "늦은 응답")
        Thread.currentThread().interrupt()

        // when & then
        shouldThrow<GpuClusterApiFailedException> { openChat(endpoint) }
        Thread.interrupted() shouldBe true
    }

    private fun readServedModel(endpoint: EksClusterEndpoint) =
        gateway.getServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")

    private fun openChat(endpoint: EksClusterEndpoint) = gateway.openModelChat(
        target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b", """{"model":"m"}""".toByteArray(), Duration.ofSeconds(5),
    )

    @Test
    fun `토큰을 만들다 STS 오류가 나면 쿠버네티스 호출이 클러스터 실패로 알린다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(201, calls = calls)
        sts.failure = stsException(500, "ServiceUnavailable")

        // when & then
        shouldThrow<GpuClusterApiFailedException> { gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm") }
        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
        shouldThrow<GpuClusterApiFailedException> { gateway.deleteServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b") }
        calls.shouldBeEmpty()
    }

    @Test
    fun `토큰을 만들다 네트워크 오류가 나면 쿠버네티스 호출이 클러스터 실패로 알린다`() {
        // given
        val endpoint = kubernetesAnswering(201)
        sts.failure = SdkClientException.create("timeout")

        // when & then
        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `토큰을 만들 때 STS 가 거부하면 역할을 맡을 수 없다고 알린다`() {
        // given
        val endpoint = kubernetesAnswering(201)
        sts.failure = stsException(403, "AccessDenied")

        // when & then
        shouldThrow<AwsRoleNotAssumableException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `API 서버 주소가 잘못되면 클러스터 실패로 알린다`() {
        val endpoint = EksClusterEndpoint(endpoint = "not a url", caData = "")

        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `쿠버네티스가 201 이면 ServedModel 을 만든 것이고 토큰과 매니페스트를 보낸다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(201, calls = calls)

        // when
        gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST)

        // then
        val call = calls.single()
        call.method shouldBe "POST"
        call.path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels"
        call.authorization!!.startsWith("Bearer k8s-aws-v1.") shouldBe true
        call.contentType shouldBe "application/json"
        JsonMapper.builder().build().readValue(call.body, Map::class.java) shouldBe mapOf(
            "apiVersion" to "serving.kukie.io/v1alpha1",
            "kind" to "ServedModel",
            "metadata" to mapOf("name" to "gpt-oss-20b", "namespace" to "llm"),
            "spec" to mapOf(
                "source" to mapOf("huggingface" to "openai/gpt-oss-20b"),
                "engine" to mapOf(
                    "image" to "engine@sha256:abc",
                    "args" to listOf("--max-model-len", "16384"),
                    "gpus" to 1,
                ),
                "storage" to mapOf("size" to "40Gi", "retainOnDelete" to true),
            ),
        )
    }

    @Test
    fun `GPU 1장이면 ServedModel 본문에 자원을 넣지 않아 오븐 기본값을 쓴다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(201, calls = calls)

        // when
        gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST)

        // then
        calls.single().body shouldBe
            """{"apiVersion":"serving.kukie.io/v1alpha1","kind":"ServedModel",""" +
            """"metadata":{"name":"gpt-oss-20b","namespace":"llm"},""" +
            """"spec":{"source":{"huggingface":"openai/gpt-oss-20b"},""" +
            """"engine":{"image":"engine@sha256:abc","args":["--max-model-len","16384"],"gpus":1},""" +
            """"storage":{"size":"40Gi","retainOnDelete":true}}}"""
    }

    @Test
    fun `GPU 여러 장이면 오븐 CRD 의 ResourceRequirements 모양으로 자원을 보낸다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(201, calls = calls)
        val manifest = MANIFEST.copy(
            gpus = 4,
            engineResources = EngineResources(cpuRequest = 16, memoryRequestGi = 128, memoryLimitGi = 256),
        )

        // when
        gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, manifest)

        // then
        val spec = JsonMapper.builder().build().readValue(calls.single().body, Map::class.java)["spec"] as Map<*, *>
        val engine = spec["engine"] as Map<*, *>
        engine["gpus"] shouldBe 4
        engine["resources"] shouldBe mapOf(
            "requests" to mapOf("cpu" to "16", "memory" to "128Gi"),
            "limits" to mapOf("memory" to "256Gi"),
        )
    }

    @Test
    fun `ServedModel 을 만들 때 쿠버네티스가 404 면 애드온이 없는 것이다`() {
        val endpoint = kubernetesAnswering(404)

        shouldThrow<GpuAddonNotInstalledException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403])
    fun `ServedModel 을 만들 때 쿠버네티스가 거부하면 권한 없음이다`(status: Int) {
        val endpoint = kubernetesAnswering(status)

        shouldThrow<GpuServingDeniedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `ServedModel 을 만들 때 쿠버네티스가 409 면 이미 있는 것이다`() {
        val endpoint = kubernetesAnswering(409)

        shouldThrow<ServedModelExistsException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @ParameterizedTest
    @ValueSource(ints = [422, 500])
    fun `ServedModel 을 만들 때 그 밖의 응답은 클러스터 실패로 알린다`(status: Int) {
        val endpoint = kubernetesAnswering(status)

        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `API 서버에 닿지 못하면 클러스터 실패로 알린다`() {
        // given — 응답한 뒤 닫아서 아무도 듣지 않는 주소를 만든다
        val endpoint = kubernetesAnswering(200)
        kubernetes?.stop(0)

        // when & then
        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `CA 를 읽지 못하면 클러스터 실패로 알린다`() {
        val endpoint = EksClusterEndpoint(endpoint = "https://example.invalid", caData = "not base64 !!")

        shouldThrow<GpuClusterApiFailedException> { gateway.createServedModel(target, "ap-northeast-2", "demo", endpoint, MANIFEST) }
    }

    @Test
    fun `namespace 에 ServedModel 이 있는지 하나만 물어서 본다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, """{"items":[{"metadata":{"name":"other"}}]}""", calls)

        // when
        val present = gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm")

        // then
        present shouldBe true
        calls.single().run {
            method shouldBe "GET"
            path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels?limit=1"
        }
    }

    @Test
    fun `목록이 비어 있으면 ServedModel 이 없는 것이다`() {
        val endpoint = kubernetesAnswering(200, """{"items":[]}""")

        gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm") shouldBe false
    }

    @Test
    fun `목록을 읽을 때 404 면 CRD 가 없는 것이다`() {
        val endpoint = kubernetesAnswering(404)

        shouldThrow<GpuAddonNotInstalledException> { gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm") }
    }

    @Test
    fun `목록을 읽을 때 403 이면 권한 없음이다`() {
        val endpoint = kubernetesAnswering(403)

        shouldThrow<GpuServingDeniedException> { gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm") }
    }

    @Test
    fun `목록 응답을 읽지 못하면 클러스터 실패로 알린다`() {
        val endpoint = kubernetesAnswering(200, "not json")

        shouldThrow<GpuClusterApiFailedException> { gateway.hasServedModel(target, "ap-northeast-2", "demo", endpoint, "llm") }
    }

    @Test
    fun `ServedModel 을 이름으로 지운다`() {
        // given
        val calls = mutableListOf<KubernetesCall>()
        val endpoint = kubernetesAnswering(200, calls = calls)

        // when
        gateway.deleteServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")

        // then
        calls.single().run {
            method shouldBe "DELETE"
            path shouldBe "/apis/serving.kukie.io/v1alpha1/namespaces/llm/servedmodels/gpt-oss-20b"
        }
    }

    @Test
    fun `지울 때 404 면 없는 모델이다`() {
        val endpoint = kubernetesAnswering(404)

        shouldThrow<ServedModelNotFoundException> {
            gateway.deleteServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")
        }
    }

    @Test
    fun `지울 때 403 이면 권한 없음이다`() {
        val endpoint = kubernetesAnswering(403)

        shouldThrow<GpuServingDeniedException> {
            gateway.deleteServedModel(target, "ap-northeast-2", "demo", endpoint, "llm", "gpt-oss-20b")
        }
    }

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
    fun `끝까지 401 만 받으면 인증이 안 된다고 본다`() {
        // given
        val responses = ArrayDeque(listOf(401, 401, 401))

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.UNAUTHENTICATED
        responses.shouldBeEmpty()
    }

    @Test
    fun `403 을 한 번이라도 받았으면 인증은 된 것이라 권한이 퍼지는 중으로 본다`() {
        // given
        val responses = ArrayDeque(listOf(401, 403, 401))

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.GRANT_PENDING
    }

    @Test
    fun `401 뒤에 오류가 나면 인증 실패로 단정하지 않는다`() {
        // given
        val responses = ArrayDeque(listOf(401, 503))

        // when & then
        gateway.probeNamespace(attempts = 3, delay = Duration.ZERO) { responses.removeFirst() } shouldBe NamespaceStatus.GRANT_PENDING
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
    fun `등록에 성공하면 만든 entry 를 지우지 않는다`() {
        // given
        val eks = FakeEksClient(denyGroups = true)

        // when
        gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "kukie-demo", AccessLevel.LIMITED)

        // then
        eks.deletions shouldBe 0
    }

    @Test
    fun `제한된 권한의 등록이 거부되면 이번에 만든 entry 를 지우고 거부를 던진다`() {
        // given — AllowedNamespaces 밖의 namespace 면 붙이기가 거부된다
        val eks = FakeEksClient(denyGroups = true, deniedAssociations = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "default", AccessLevel.LIMITED)
        }
        eks.deletions shouldBe 1
    }

    @Test
    fun `전체 권한의 등록이 거부되어도 이번에 만든 entry 를 지우고 거부를 던진다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, deniedAssociations = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "kukie-demo", AccessLevel.FULL)
        }
        eks.deletions shouldBe 1
    }

    @Test
    fun `등록이 거부되어도 이미 있던 entry 는 다른 namespace 가 쓰므로 지우지 않는다`() {
        // given
        val eks = FakeEksClient(
            denyGroups = true,
            exists = true,
            associated = listOf(associated(EDIT_POLICY, namespaceScope("kukie-app"))),
            deniedAssociations = Int.MAX_VALUE,
        )

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "default", AccessLevel.LIMITED)
        }
        eks.deletions shouldBe 0
    }

    @Test
    fun `이번에 만든 entry 라도 그사이 다른 등록이 정책을 붙였으면 지우지 않는다`() {
        // given
        val eks = FakeEksClient(
            denyGroups = true,
            deniedAssociations = Int.MAX_VALUE,
            associatedLater = listOf(associated(EDIT_POLICY, namespaceScope("kukie-app"))),
        )

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "default", AccessLevel.LIMITED)
        }
        eks.deletions shouldBe 0
    }

    @Test
    fun `만든 entry 를 지우지 못해도 처음 거부를 던진다`() {
        // given
        val eks = FakeEksClient(
            denyGroups = true,
            deniedAssociations = Int.MAX_VALUE,
            deleteFailure = eksException(500, "ServerException", "boom"),
        )

        // when & then — 지우기 오류로 바뀌면 호출부가 EksAccessDeniedException 으로 옮기지 못한다
        shouldThrow<AccessDeniedException> {
            gateway.registerSelfOn(eks, "kukie-gpu", PRINCIPAL, "default", AccessLevel.LIMITED)
        }
        eks.deletions shouldBe 1
    }

    @Test
    fun `entry 를 만든 지 오래됐으면 권한이 퍼지기를 다 기다린 것으로 본다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, entryCreatedAt = Instant.now().minus(Duration.ofHours(8)))

        // when & then
        gateway.selfEntrySettledOn(eks, "kukie-gpu", PRINCIPAL) shouldBe true
    }

    @Test
    fun `entry 를 막 만들었거나 만든 시각을 모르면 아직 기다리는 중으로 본다`() {
        // when & then
        val recent = FakeEksClient(denyGroups = true, entryCreatedAt = Instant.now().minusSeconds(30))
        gateway.selfEntrySettledOn(recent, "kukie-gpu", PRINCIPAL) shouldBe false
        gateway.selfEntrySettledOn(FakeEksClient(denyGroups = true), "kukie-gpu", PRINCIPAL) shouldBe false
    }

    @Test
    fun `없는 entry 지우기가 템플릿 조건에 거부되면 그대로 던진다`() {
        // given
        val eks = FakeEksClient(
            denyGroups = true,
            deleteFailure = AccessDeniedException.builder().statusCode(403).message("not authorized").build(),
        )

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.unregisterSelfOn(eks, "kukie-gpu", PRINCIPAL, emptySet(), AccessLevel.FULL)
        }
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
    fun `옛 정책 떼기가 거부되어도 다시 해서 관리자 정책을 남기지 않는다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedDisassociations = 1)

        // when
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.LIMITED, Duration.ZERO)

        // then
        eks.policyCalls shouldBe listOf(
            "associate" to EDIT_POLICY,
            "disassociate" to CLUSTER_ADMIN_POLICY,
            "associate" to EDIT_POLICY,
            "disassociate" to CLUSTER_ADMIN_POLICY,
        )
    }

    @Test
    fun `제한된 권한으로 내릴 때 거부가 잠깐이면 붙인 뒤에만 관리자 정책을 떼어 정책 없는 entry 를 만들지 않는다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = 3)

        // when
        gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.LIMITED, Duration.ZERO)

        // then
        eks.policyCalls shouldBe List(4) { "associate" to EDIT_POLICY } + ("disassociate" to CLUSTER_ADMIN_POLICY)
        eks.associations.single().policyArn() shouldBe EDIT_POLICY
    }

    @Test
    fun `전체 권한으로 올리다 끝내 붙이지 못하면 옛 정책을 떼지 않고 던진다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.FULL, Duration.ZERO)
        }
        eks.policyCalls.map { it.first }.distinct() shouldBe listOf("associate")
    }

    @Test
    fun `제한된 권한으로 내리다 namespace 가 끝내 거부되면 재시도를 마친 뒤 관리자 정책을 떼고 던진다`() {
        // given — 등록한 namespace 가 AllowedNamespaces 밖이면 붙이기는 몇 번을 해도 거부된다
        val eks = FakeEksClient(denyGroups = true, exists = true, deniedAssociations = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("default"), AccessLevel.LIMITED, Duration.ZERO)
        }
        eks.policyCalls shouldBe List(4) { "associate" to EDIT_POLICY } + ("disassociate" to CLUSTER_ADMIN_POLICY)
    }

    @Test
    fun `제한된 권한으로 내리다 entry 만들기가 끝내 거부되어도 관리자 정책을 뗀다`() {
        // given
        val eks = FakeEksClient(denyGroups = true, deniedEntries = Int.MAX_VALUE)

        // when & then
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-demo"), AccessLevel.LIMITED, Duration.ZERO)
        }
        eks.policyCalls shouldBe listOf("disassociate" to CLUSTER_ADMIN_POLICY)
    }

    @Test
    fun `제한된 권한으로 내리다 관리자 정책 떼기까지 실패해도 처음 거부를 던진다`() {
        // given
        val eks = FakeEksClient(
            denyGroups = true,
            exists = true,
            deniedAssociations = Int.MAX_VALUE,
            disassociateFailure = eksException(500, "ServerException", "boom"),
        )

        // when & then — 떼기 오류로 바뀌면 호출부가 EksAccessDeniedException 으로 옮기지 못한다
        shouldThrow<AccessDeniedException> {
            gateway.changeAccessLevelOn(eks, "kukie-gpu", PRINCIPAL, setOf("default"), AccessLevel.LIMITED, Duration.ZERO)
        }
        eks.policyCalls.last() shouldBe ("disassociate" to CLUSTER_ADMIN_POLICY)
    }

    @Test
    fun `제한된 권한의 등록을 지우면 남은 namespace 로 Edit 범위를 좁히고 남아 있을 수 있는 관리자 정책을 뗀다`() {
        // given — 관리자 정책은 이미 없다
        val eks = FakeEksClient(denyGroups = true, detachedAlready = true)

        // when
        gateway.unregisterSelfOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-app"), AccessLevel.LIMITED)

        // then
        eks.policyCalls shouldBe listOf("associate" to EDIT_POLICY, "disassociate" to CLUSTER_ADMIN_POLICY)
        eks.associations.single().accessScope().namespaces() shouldBe listOf("kukie-app")
    }

    @Test
    fun `전체 권한의 등록을 지워도 같은 EKS 의 다른 등록이 남아 있으면 정책을 그대로 둔다`() {
        // given
        val eks = FakeEksClient(denyGroups = true)

        // when
        gateway.unregisterSelfOn(eks, "kukie-gpu", PRINCIPAL, setOf("kukie-app"), AccessLevel.FULL)

        // then
        eks.policyCalls.shouldBeEmpty()
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
        private val GPU_NODE_GROUP = EksNodegroupState("gpu", listOf("g6e.xlarge"), "ACTIVE", desired = 1, min = 1, max = 3)
        private val MANIFEST = ServedModelManifest(
            namespace = "llm",
            name = "gpt-oss-20b",
            model = "openai/gpt-oss-20b",
            engineImage = "engine@sha256:abc",
            engineArgs = listOf("--max-model-len", "16384"),
            gpus = 1,
            engineResources = null,
            storageGi = 40,
            retainOnDelete = true,
        )
        private const val EDIT_POLICY = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSEditPolicy"
        private const val CLUSTER_ADMIN_POLICY = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
        private const val INVALID_TOKEN = "The security token included in the request is invalid."
        private const val PRINCIPAL = "arn:aws:iam::123456789012:role/KukieAccess-kukie-0123456789abcdef"
    }

    /**
     * 고객 역할의 IAM 판단만 흉내 낸다: 그룹을 단 요청은 denyGroups 면 거부, 모든 요청은 denyAll 이면 거부.
     * 그룹 없는 entry 만들기는 처음 deniedEntries 번, 정책 붙이기는 처음 deniedAssociations 번 거부하고,
     * 떼기는 처음 deniedDisassociations 번 거부하고, disassociateFailure 를 주면 그 오류로 실패하고,
     * detachedAlready 면 붙어 있지 않다고 답한다. 정책 목록은 associatedLater 를 주면 두 번째 조회부터 그 값이고,
     * entry 지우기는 deleteFailure 를 주면 그 오류로 실패하며, entry 의 만든 시각은 entryCreatedAt 이다.
     */
    private class FakeEksClient(
        private val denyGroups: Boolean,
        private val denyAll: Boolean = false,
        private val exists: Boolean = false,
        private var deniedEntries: Int = 0,
        private val associated: List<AssociatedAccessPolicy> = emptyList(),
        private var deniedAssociations: Int = 0,
        private val detachedAlready: Boolean = false,
        private var deniedDisassociations: Int = 0,
        private val disassociateFailure: EksException? = null,
        private val associatedLater: List<AssociatedAccessPolicy>? = null,
        private val deleteFailure: RuntimeException? = null,
        private val entryCreatedAt: Instant? = null,
    ) : EksClient {
        val requests = mutableListOf<CreateAccessEntryRequest>()
        val associations = mutableListOf<AssociateAccessPolicyRequest>()
        val policyCalls = mutableListOf<Pair<String, String>>()
        var deletions = 0
        private var listCalls = 0

        override fun listAssociatedAccessPolicies(request: ListAssociatedAccessPoliciesRequest): ListAssociatedAccessPoliciesResponse {
            val policies = if (listCalls++ == 0) associated else associatedLater ?: associated
            return ListAssociatedAccessPoliciesResponse.builder().associatedAccessPolicies(policies).build()
        }

        override fun deleteAccessEntry(request: DeleteAccessEntryRequest): DeleteAccessEntryResponse {
            deletions++
            deleteFailure?.let { throw it }
            return DeleteAccessEntryResponse.builder().build()
        }

        override fun describeAccessEntry(request: DescribeAccessEntryRequest): DescribeAccessEntryResponse =
            DescribeAccessEntryResponse.builder().accessEntry(AccessEntry.builder().createdAt(entryCreatedAt).build()).build()

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
            disassociateFailure?.let { throw it }
            if (deniedDisassociations > 0) {
                deniedDisassociations--
                throw AccessDeniedException.builder().statusCode(403).message("not authorized").build()
            }
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

    /** 로컬에 가짜 API 서버를 띄운다. 평문 HTTP 라 CA 는 쓰이지 않아 빈 값을 준다. */
    private fun kubernetesAnswering(
        status: Int,
        body: String = "",
        calls: MutableList<KubernetesCall> = mutableListOf(),
    ): EksClusterEndpoint {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls += KubernetesCall(
                method = exchange.requestMethod,
                path = exchange.requestURI.toString(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8),
            )
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
            exchange.close()
        }
        server.start()
        kubernetes = server
        return EksClusterEndpoint(endpoint = "http://127.0.0.1:${server.address.port}", caData = "")
    }

    private fun kubernetesRouting(answer: (String) -> Pair<Int, String>): EksClusterEndpoint {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val (status, body) = answer(exchange.requestURI.toString())
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
            exchange.close()
        }
        server.start()
        kubernetes = server
        return EksClusterEndpoint(endpoint = "http://127.0.0.1:${server.address.port}", caData = "")
    }

    private data class KubernetesCall(
        val method: String,
        val path: String,
        val authorization: String?,
        val contentType: String?,
        val body: String,
    )

    private fun nodegroup(instanceType: String): Nodegroup =
        Nodegroup.builder()
            .instanceTypes(instanceType)
            .status("ACTIVE")
            .scalingConfig(NodegroupScalingConfig.builder().desiredSize(1).minSize(0).maxSize(2).build())
            .build()

    private class NodegroupEksClient(
        private val names: List<String> = emptyList(),
        private val nodegroups: Map<String, Nodegroup> = emptyMap(),
        private val updateFailure: RuntimeException? = null,
    ) : EksClient {
        val updates = mutableListOf<UpdateNodegroupConfigRequest>()

        override fun listNodegroups(request: ListNodegroupsRequest): ListNodegroupsResponse =
            ListNodegroupsResponse.builder().nodegroups(names).build()

        override fun describeNodegroup(request: DescribeNodegroupRequest): DescribeNodegroupResponse {
            val nodegroup = nodegroups[request.nodegroupName()]
                ?: throw ResourceNotFoundException.builder().statusCode(404).message("gone").build()
            return DescribeNodegroupResponse.builder().nodegroup(nodegroup).build()
        }

        override fun updateNodegroupConfig(request: UpdateNodegroupConfigRequest): UpdateNodegroupConfigResponse {
            updateFailure?.let { throw it }
            updates += request
            return UpdateNodegroupConfigResponse.builder().build()
        }

        override fun serviceName(): String = "eks"

        override fun close() = Unit
    }

    private class FakeStsClient : StsClient {
        var calls = 0
        var failure: RuntimeException? = null

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
