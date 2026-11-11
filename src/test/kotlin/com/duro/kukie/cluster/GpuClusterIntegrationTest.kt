package com.duro.kukie.cluster

import com.duro.kukie.cluster.application.EksCredential
import com.duro.kukie.cluster.application.port.out.AwsTarget
import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.cluster.application.port.out.EksNodegroupState
import com.duro.kukie.cluster.domain.AccessLevel
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.domain.ClusterProvider
import com.duro.kukie.cluster.domain.ClusterRepository
import com.duro.kukie.cluster.exception.ClusterErrorCode
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.GpuAddonNotInstalledException
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuNodeControlDeniedException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.duro.kukie.cluster.application.GpuAuditLog
import com.duro.kukie.cluster.application.port.out.EngineResources
import com.duro.kukie.cluster.application.port.out.PrecheckPodState
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.cluster.presentation.ModelChatBodyLimit
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.support.FakeEksAccessGateway
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_CLUSTER_NAME
import com.duro.kukie.support.FakeEksAccessGateway.Companion.DEFAULT_REGION
import com.duro.kukie.support.IntegrationTest
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.exception.TeamErrorCode
import com.duro.kukie.user.UserFixture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class GpuClusterIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakeEksAccessGateway: FakeEksAccessGateway

    @Autowired
    private lateinit var awsConnectionRepository: AwsConnectionRepository

    @Autowired
    private lateinit var clusterRepository: ClusterRepository

    @Autowired
    private lateinit var credentialCipher: CredentialCipher

    @Test
    fun `GPU 노드 그룹만 보여 주고 full 에 GpuNodeControl 이 켜져 있으면 control 이 true 다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") {
            authorization(setup.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supported") { value(true) }
            jsonPath("$.control") { value(true) }
            jsonPath("$.nodeGroups.length()") { value(1) }
            jsonPath("$.nodeGroups[0].name") { value("gpu") }
            jsonPath("$.nodeGroups[0].instanceTypes[0]") { value("g6e.xlarge") }
            jsonPath("$.nodeGroups[0].status") { value("ACTIVE") }
            jsonPath("$.nodeGroups[0].desired") { value(0) }
            jsonPath("$.nodeGroups[0].min") { value(0) }
            jsonPath("$.nodeGroups[0].max") { value(2) }
        }
    }

    @Test
    fun `g 나 p 뒤에 숫자가 오는 인스턴스만 GPU 노드 그룹이다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)
        fakeEksAccessGateway.nodegroupStates = listOf(
            nodegroup("graviton", "m7g.large"),
            nodegroup("memory", "r6i.large"),
            nodegroup("gr", "gr6.4xlarge"),
            nodegroup("p", "p5.48xlarge"),
            nodegroup("mixed", "t3.large", "g5.xlarge"),
        )

        // when & then
        mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") {
            authorization(setup.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.nodeGroups.length()") { value(2) }
            jsonPath("$.nodeGroups[0].name") { value("p") }
            jsonPath("$.nodeGroups[1].name") { value("mixed") }
        }
    }

    @Test
    fun `limited 면 GpuNodeControl 이 켜져 있어도 control 이 false 다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.LIMITED, gpuNodeControl = true)

        // when & then
        getControl(setup).andExpect { jsonPath("$.control") { value(false) } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["demo", "de*", "d?mo", "*"])
    fun `클러스터 이름이 GpuClusterPattern 에 맞으면 control 이 true 다`(pattern: String) {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = pattern)

        // when & then
        getControl(setup).andExpect { jsonPath("$.control") { value(true) } }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["other", "de?", "Demo"])
    fun `클러스터 이름이 GpuClusterPattern 밖이거나 패턴이 없으면 control 이 false 다`(pattern: String?) {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = pattern)

        // when & then
        getControl(setup).andExpect { jsonPath("$.control") { value(false) } }
    }

    @Test
    fun `패턴 밖 클러스터면 AWS 를 부르지 않고 노드 수 변경을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = "other-*")

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NODE_CONTROL_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `AWS 나 클러스터가 처리하지 못하면 GET·PUT·POST·DELETE 모두 502 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        fakeEksAccessGateway.nodegroupReadFailure = GpuClusterApiFailedException()
        fakeEksAccessGateway.servedModelFailure = GpuClusterApiFailedException()

        // when & then
        listOf(
            mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") { authorization(setup.accessToken) },
            putDesired(setup, "gpu", 1),
            postModel(setup, "openai/gpt-oss-20b", 40),
            deleteModel(setup, "gpt-oss-20b"),
        ).forEach { result ->
            result.andExpect {
                status { isBadGateway() }
                jsonPath("$.code") { value(ClusterErrorCode.GPU_CLUSTER_API_FAILED.code) }
            }
        }
    }

    @Test
    fun `v5 이하 스택이면 control 이 false 다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = false)

        // when & then
        getControl(setup).andExpect { jsonPath("$.control") { value(false) } }
    }

    @Test
    fun `연결을 해제했으면 control 이 false 다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true)
        val connection = awsConnectionRepository.findByExternalId(EXTERNAL_ID)!!
        connection.disconnect()
        awsConnectionRepository.save(connection)

        // when & then
        getControl(setup).andExpect { jsonPath("$.control") { value(false) } }
    }

    @Test
    fun `EKS 가 아니면 supported 가 false 이고 목록이 비어 있다`() {
        // given
        val user = loggedInUser()
        val teamId = savedTeam(user.user.id to TeamRole.MEMBER)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = user.user.id, teamId = teamId))

        // when & then
        mockMvc.get("/clusters/${cluster.id}/gpu/node-groups") {
            authorization(user.accessToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.supported") { value(false) }
            jsonPath("$.control") { value(false) }
            jsonPath("$.nodeGroups.length()") { value(0) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `팀 구성원이 아니면 노드 그룹을 볼 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        val stranger = loggedInUser(UserFixture.user(email = "stranger@example.com"))

        // when & then
        mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") {
            authorization(stranger.accessToken)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.NOT_FOUND.code) }
        }
    }

    @Test
    fun `역할이 노드 그룹을 읽지 못하면 EKS_ACCESS_DENIED 다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)
        fakeEksAccessGateway.nodegroupReadFailure = EksAccessDeniedException()

        // when & then
        mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") {
            authorization(setup.accessToken)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.EKS_ACCESS_DENIED.code) }
        }
    }

    @Test
    fun `GPU 노드 그룹의 desired 만 바꾸고 바뀐 상태를 돌려준다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        putDesired(setup, "gpu", 2).andExpect {
            status { isOk() }
            jsonPath("$.name") { value("gpu") }
            jsonPath("$.desired") { value(2) }
            jsonPath("$.min") { value(0) }
            jsonPath("$.max") { value(2) }
            jsonPath("$.status") { value("UPDATING") }
        }
        fakeEksAccessGateway.nodegroupUpdates shouldBe listOf(
            FakeEksAccessGateway.NodegroupUpdate(TARGET, DEFAULT_REGION, DEFAULT_CLUSTER_NAME, "gpu", 2, min = 0, max = 2),
        )
    }

    @Test
    fun `이미 그 값이면 AWS 에 바꾸라고 하지 않고 지금 상태를 돌려준다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        putDesired(setup, "gpu", 0).andExpect {
            status { isOk() }
            jsonPath("$.desired") { value(0) }
            jsonPath("$.status") { value("ACTIVE") }
        }
        fakeEksAccessGateway.nodegroupUpdates.shouldBeEmpty()
    }

    @Test
    fun `팀 Admin 이 아니면 노드 수를 바꿀 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `control 이 false 면 AWS 를 부르지 않고 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = false)

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NODE_CONTROL_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `AWS 가 거부하면 GPU_NODE_CONTROL_DENIED 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        fakeEksAccessGateway.nodegroupUpdateFailure = GpuNodeControlDeniedException()

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NODE_CONTROL_DENIED.code) }
        }
    }

    @Test
    fun `노드 그룹을 읽다 거부되어도 GPU_NODE_CONTROL_DENIED 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        fakeEksAccessGateway.nodegroupReadFailure = EksAccessDeniedException()

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NODE_CONTROL_DENIED.code) }
        }
    }

    @Test
    fun `다른 변경이 진행 중이면 NODE_GROUP_BUSY 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        fakeEksAccessGateway.nodegroupUpdateFailure = NodeGroupBusyException()

        // when & then
        putDesired(setup, "gpu", 1).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.NODE_GROUP_BUSY.code) }
        }
    }

    @Test
    fun `desired 가 min 보다 작거나 max 보다 크면 INVALID_NODE_COUNT 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
        fakeEksAccessGateway.nodegroupStates = listOf(
            EksNodegroupState("gpu", listOf("g6e.xlarge"), "ACTIVE", desired = 1, min = 1, max = 2),
        )

        // when & then
        listOf(-1, 0, 3).forEach { desired ->
            putDesired(setup, "gpu", desired).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(ClusterErrorCode.INVALID_NODE_COUNT.code) }
            }
        }
        fakeEksAccessGateway.nodegroupUpdates.shouldBeEmpty()
    }

    @Test
    fun `GPU 노드 그룹이 아니거나 없거나 이름이 잘못되면 찾을 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        listOf("general", "missing", "bad.name").forEach { name ->
            putDesired(setup, name, 1).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value(ClusterErrorCode.GPU_NODE_GROUP_NOT_FOUND.code) }
            }
        }
        fakeEksAccessGateway.nodegroupUpdates.shouldBeEmpty()
    }

    @Test
    fun `desired 가 없으면 400 이다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        mockMvc.put("/clusters/${setup.clusterId}/gpu/node-groups/gpu") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
    }

    @Test
    fun `EKS 가 아니면 노드 수를 바꿀 수 없다`() {
        // given
        val user = loggedInUser()
        val teamId = savedTeam(user.user.id to TeamRole.ADMIN)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = user.user.id, teamId = teamId))

        // when & then
        putDesired(Setup(cluster.id, user.accessToken), "gpu", 1).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NOT_SUPPORTED.code) }
        }
    }

    @Test
    fun `ServedModel 을 llm namespace 에 서버 설정의 엔진으로 만든다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postModel(setup, "Qwen/Qwen3.6-35B-A3B", 120).andExpect {
            status { isCreated() }
            jsonPath("$.namespace") { value("llm") }
            jsonPath("$.name") { value("qwen3-6-35b-a3b") }
            jsonPath("$.model") { value("Qwen/Qwen3.6-35B-A3B") }
        }
        fakeEksAccessGateway.servedModelCreations.single().run {
            namespace shouldBe "llm"
            name shouldBe "qwen3-6-35b-a3b"
            model shouldBe "Qwen/Qwen3.6-35B-A3B"
            engineImage shouldBe "vllm/vllm-openai:v0.32.0@sha256:6ee133356d49ed24cee03dd0cda3d3980279a11c107ce3e752c89e76540f41c9"
            engineArgs shouldBe listOf("--max-model-len", "16384", "--gpu-memory-utilization", "0.92")
            gpus shouldBe 1
            storageGi shouldBe 120
            retainOnDelete shouldBe true
        }
        fakeEksAccessGateway.servedModelEndpoints.single().run {
            endpoint shouldBe ClusterFixture.DEFAULT_SERVER
            caData shouldBe ClusterFixture.CA
        }
    }

    @Test
    fun `v5 full 스택이면 GpuNodeControl 과 GpuClusterPattern 없이 모델을 시작한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = false, gpuClusterPattern = null)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect { status { isCreated() } }
        fakeEksAccessGateway.servedModelCreations.single().name shouldBe "gpt-oss-20b"
    }

    @Test
    fun `GpuClusterPattern 밖 클러스터여도 full 이면 모델을 시작한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = "other-*")

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect { status { isCreated() } }
        fakeEksAccessGateway.servedModelCreations.single().name shouldBe "gpt-oss-20b"
    }

    @Test
    fun `gpus 를 안 보내거나 null 이면 1장으로 시작하고 자원을 넣지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect { status { isCreated() } }
        postModel(setup, "openai/gpt-oss-20b", 40, gpusJson = "null").andExpect { status { isCreated() } }
        postModel(setup, "openai/gpt-oss-20b", 40, gpusJson = "1").andExpect { status { isCreated() } }

        // then
        fakeEksAccessGateway.servedModelCreations.map { it.gpus to it.engineResources } shouldBe List(3) { 1 to null }
    }

    @Test
    fun `gpus 가 2 4 8 이면 장수에 맞춘 자원으로 시작한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when
        listOf(2, 4, 8).forEach { gpus ->
            postModel(setup, "openai/gpt-oss-120b", 100, gpusJson = "$gpus").andExpect { status { isCreated() } }
        }

        // then
        fakeEksAccessGateway.servedModelCreations.map { it.gpus to it.engineResources } shouldBe listOf(
            2 to EngineResources(cpuRequest = 8, memoryRequestGi = 64, memoryLimitGi = 128),
            4 to EngineResources(cpuRequest = 16, memoryRequestGi = 128, memoryLimitGi = 256),
            8 to EngineResources(cpuRequest = 32, memoryRequestGi = 256, memoryLimitGi = 512),
        )
    }

    @Test
    fun `감사 로그에 GPU 수를 남긴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val logger = LoggerFactory.getLogger(GpuAuditLog::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        // when
        try {
            postModel(setup, "openai/gpt-oss-120b", 100, gpusJson = "4").andExpect { status { isCreated() } }
            postModel(setup, "openai/gpt-oss-20b", 40).andExpect { status { isCreated() } }
        } finally {
            logger.detachAppender(appender)
        }

        // then
        appender.list.map { it.formattedMessage.substringAfter("action=") } shouldBe listOf(
            "start-model namespace=llm name=gpt-oss-120b model=openai/gpt-oss-120b storageGi=100 gpus=4",
            "start-model namespace=llm name=gpt-oss-20b model=openai/gpt-oss-20b storageGi=40 gpus=1",
        )
    }

    @Test
    fun `팀 Admin 이 아니면 모델을 시작할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `namespace 에 다른 ServedModel 이 있으면 만들지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelPresent = true

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_EXISTS.code) }
        }
        fakeEksAccessGateway.servedModelCreations.shouldBeEmpty()
    }

    @Test
    fun `쿠버네티스가 이미 있다고 하면 SERVED_MODEL_EXISTS 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelCreateFailure = ServedModelExistsException()

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_EXISTS.code) }
        }
    }

    @Test
    fun `CRD 나 namespace 가 없으면 GPU_ADDON_NOT_INSTALLED 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelFailure = GpuAddonNotInstalledException()

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_ADDON_NOT_INSTALLED.code) }
        }
    }

    @Test
    fun `쿠버네티스가 거부하면 GPU_SERVING_DENIED 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelFailure = GpuServingDeniedException()

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
    }

    @Test
    fun `limited 면 GpuNodeControl 이 켜져 있어도 쿠버네티스를 부르지 않고 모델 시작을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED, gpuNodeControl = true)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `연결을 해제했으면 쿠버네티스를 부르지 않고 모델 시작과 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val connection = awsConnectionRepository.findByExternalId(EXTERNAL_ID)!!
        connection.disconnect()
        awsConnectionRepository.save(connection)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `연결이 다른 계정으로 바뀌었으면 쿠버네티스를 부르지 않고 모델 시작과 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val connection = awsConnectionRepository.findByExternalId(EXTERNAL_ID)!!
        connection.connect("210987654321", LocalDateTime.now(), null, AccessLevel.FULL)
        awsConnectionRepository.save(connection)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `스택을 limited 로 내렸으면 클러스터가 아직 full 이어도 모델 시작과 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val connection = awsConnectionRepository.findByExternalId(EXTERNAL_ID)!!
        connection.connect(ACCOUNT_ID, LocalDateTime.now(), null, AccessLevel.LIMITED)
        awsConnectionRepository.save(connection)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `모델 형식이 CRD 패턴과 다르거나 저장 공간이 범위를 벗어나면 400 이다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        listOf(
            "no-slash" to 40,
            "a/b/c" to 40,
            "-org/name" to 40,
            "org/" + "a".repeat(200) to 40,
            "openai/gpt-oss-20b" to 19,
            "openai/gpt-oss-20b" to 501,
        ).forEach { (model, storageGi) ->
            postModel(setup, model, storageGi).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
            }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `저장 공간의 경계값은 받는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        listOf(20, 500).forEach { storageGi ->
            postModel(setup, "openai/gpt-oss-20b", storageGi).andExpect { status { isCreated() } }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "3", "5", "6", "7", "9", "16", "2.5", "2.0", "99999999999", "\"2\"", "\"four\"", "\"\"", "true", "[2]"])
    fun `gpus 가 1 2 4 8 이 아니면 쿠버네티스를 부르지 않고 400 이다`(gpusJson: String) {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40, gpusJson = gpusJson).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(GlobalErrorCode.BAD_REQUEST.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `gpus 가 허용하지 않는 정수면 고를 수 있는 값을 알려 준다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40, gpusJson = "3").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("GPU 수는 1, 2, 4, 8 중 하나여야 합니다.") }
        }
    }

    @Test
    fun `EKS 가 아니면 모델을 시작하거나 중지할 수 없다`() {
        // given
        val user = loggedInUser()
        val teamId = savedTeam(user.user.id to TeamRole.ADMIN)
        val cluster = clusterRepository.save(ClusterFixture.cluster(registeredBy = user.user.id, teamId = teamId))
        val setup = Setup(cluster.id, user.accessToken)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NOT_SUPPORTED.code) }
        }
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_NOT_SUPPORTED.code) }
        }
    }

    @Test
    fun `ServedModel 을 llm namespace 에서 지운다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect { status { isNoContent() } }
        fakeEksAccessGateway.servedModelDeletions shouldBe listOf(
            FakeEksAccessGateway.ServedModelDeletion("llm", "gpt-oss-20b"),
        )
    }

    @Test
    fun `v5 full 스택이면 GpuNodeControl 과 GpuClusterPattern 없이 모델을 중지한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = false, gpuClusterPattern = null)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect { status { isNoContent() } }
        fakeEksAccessGateway.servedModelDeletions shouldBe listOf(
            FakeEksAccessGateway.ServedModelDeletion("llm", "gpt-oss-20b"),
        )
    }

    @Test
    fun `GpuClusterPattern 밖 클러스터여도 full 이면 모델을 중지한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = "other-*")

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect { status { isNoContent() } }
        fakeEksAccessGateway.servedModelDeletions shouldBe listOf(
            FakeEksAccessGateway.ServedModelDeletion("llm", "gpt-oss-20b"),
        )
    }

    @Test
    fun `팀 Admin 이 아니면 모델을 중지할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `없는 ServedModel 을 지우면 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelFailure = ServedModelNotFoundException()

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
        }
    }

    @Test
    fun `쿠버네티스 이름이 아니면 쿠버네티스를 부르지 않고 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        listOf("Upper", "a..b", "bad_name").forEach { name ->
            deleteModel(setup, name).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
            }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `limited 면 GpuNodeControl 이 켜져 있어도 쿠버네티스를 부르지 않고 모델 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED, gpuNodeControl = true)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `모델이 Ready 가 아니거나 지우는 중이면 대화를 시작하지 않고 MODEL_NOT_READY 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        listOf(
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Loading"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Stopped"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = null),
            FakeEksAccessGateway.READY_MODEL.copy(deleting = true),
        ).forEach { state ->
            fakeEksAccessGateway.servedModelState = state

            // when & then
            postChat(setup, "gpt-oss-20b").andExpect {
                status { isConflict() }
                jsonPath("$.code") { value(ClusterErrorCode.MODEL_NOT_READY.code) }
            }
        }
    }

    @Test
    fun `스트림을 기다리는 요청의 거절도 JSON 본문으로 받는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED)

        // when & then
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models/gpt-oss-20b/chat") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.TEXT_EVENT_STREAM
            content = CHAT_BODY
        }.andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
    }

    @Test
    fun `없는 모델과 대화하면 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelState = null

        // when & then
        postChat(setup, "gpt-oss-20b").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
        }
    }

    @Test
    fun `쿠버네티스 이름이 아니면 쿠버네티스를 부르지 않고 대화와 연장이 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postChat(setup, "Bad_Name").andExpect { status { isNotFound() } }
        postExtend(setup, "Bad_Name", 1).andExpect { status { isNotFound() } }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `ServedModel 의 모델 이름이 규칙에 맞지 않으면 그 값을 쓰지 않고 실패로 알린다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelState = FakeEksAccessGateway.READY_MODEL.copy(model = "bad model\nforged=log")

        // when & then
        postChat(setup, "gpt-oss-20b").andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_CLUSTER_API_FAILED.code) }
        }
    }

    @Test
    fun `팀 Admin 이 아니면 대화하거나 연장할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)

        // when & then
        postChat(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        postExtend(setup, "gpt-oss-20b", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `limited 면 쿠버네티스를 부르지 않고 대화와 연장을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED)

        // when & then
        postChat(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        postExtend(setup, "gpt-oss-20b", 1).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"messages":[]}""",
            """{}""",
            """{"messages":[null]}""",
            """{"messages":[{"role":"tool","content":"x"}]}""",
            """{"messages":[{"role":"user"}]}""",
            """{"messages":[{"content":"x"}]}""",
            """{"messages":[{"role":"user","content":"x"}],"maxTokens":0}""",
            """{"messages":[{"role":"user","content":"x"}],"maxTokens":2049}""",
            """{"messages":[{"role":"user","content":"x"}],"temperature":-0.1}""",
            """{"messages":[{"role":"user","content":"x"}],"temperature":2.1}""",
        ],
    )
    fun `대화 요청 형식이 틀리면 쿠버네티스를 부르지 않고 400 이다`(body: String) {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postChat(setup, "gpt-oss-20b", body).andExpect { status { isBadRequest() } }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `대화 요청이 크기 상한을 넘으면 413 이다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val huge = "가".repeat(ModelChatBodyLimit.MAX_BYTES / 3 + 1)

        // when & then
        postChat(setup, "gpt-oss-20b", """{"messages":[{"role":"user","content":"$huge"}]}""").andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value(ClusterErrorCode.MODEL_CHAT_TOO_LARGE.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `연장하면 지금부터 그 시간 뒤를 annotation 에 읽은 버전과 함께 쓴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val before = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        // when
        val response = postExtend(setup, "gpt-oss-20b", 2).andExpect { status { isOk() } }.andReturn().response.contentAsString

        // then
        val after = Instant.now()
        val write = fakeEksAccessGateway.keepUntilWrites.single()
        write.namespace shouldBe "llm"
        write.name shouldBe "gpt-oss-20b"
        write.resourceVersion shouldBe "7"
        (write.keepUntil >= before.plus(2, ChronoUnit.HOURS)) shouldBe true
        (write.keepUntil <= after.plus(2, ChronoUnit.HOURS)) shouldBe true
        response shouldBe """{"keepUntil":"${write.keepUntil}"}"""
    }

    @Test
    fun `이미 더 늦게 멈출 예정이면 연장해도 그 시각을 줄이지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val later = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)
        val laterStill = later.plus(30, ChronoUnit.MINUTES)

        listOf(
            FakeEksAccessGateway.READY_MODEL.copy(stopsAt = later),
            FakeEksAccessGateway.READY_MODEL.copy(keepUntil = laterStill, stopsAt = later),
        ).zip(listOf(later, laterStill)).forEach { (state, expected) ->
            fakeEksAccessGateway.servedModelState = state

            // when & then
            postExtend(setup, "gpt-oss-20b", 1).andExpect {
                status { isOk() }
                jsonPath("$.keepUntil") { value(expected.toString()) }
            }
            fakeEksAccessGateway.keepUntilWrites.last().keepUntil shouldBe expected
        }
    }

    @Test
    fun `쓰는 사이 ServedModel 이 바뀌면 다시 읽고 쓴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.keepUntilConflicts = 2

        // when & then
        postExtend(setup, "gpt-oss-20b", 1).andExpect { status { isOk() } }
        fakeEksAccessGateway.keepUntilWrites.size shouldBe 1
    }

    @Test
    fun `계속 충돌하면 실패로 알린다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.keepUntilConflicts = 3

        // when & then
        postExtend(setup, "gpt-oss-20b", 1).andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_CLUSTER_API_FAILED.code) }
        }
        fakeEksAccessGateway.keepUntilWrites.shouldBeEmpty()
    }

    @Test
    fun `이미 멈췄거나 지우는 중인 모델은 연장하지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        listOf(
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Stopped"),
            FakeEksAccessGateway.READY_MODEL.copy(deleting = true),
        ).forEach { state ->
            fakeEksAccessGateway.servedModelState = state

            // when & then
            postExtend(setup, "gpt-oss-20b", 1).andExpect {
                status { isConflict() }
                jsonPath("$.code") { value(ClusterErrorCode.MODEL_NOT_READY.code) }
            }
        }
        fakeEksAccessGateway.keepUntilWrites.shouldBeEmpty()
    }

    @Test
    fun `아직 뜨는 중인 모델도 자동 중지를 미룰 수 있다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelState = FakeEksAccessGateway.READY_MODEL.copy(phase = "Loading")

        // when & then
        postExtend(setup, "gpt-oss-20b", 1).andExpect { status { isOk() } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"hours":0}""", """{"hours":5}""", """{}""", """{"hours":"많이"}"""])
    fun `연장 시간이 1 에서 4 시간이 아니면 400 이다`(body: String) {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models/gpt-oss-20b/extend") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isBadRequest() } }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `없는 모델을 연장하면 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelState = null

        // when & then
        postExtend(setup, "gpt-oss-20b", 1).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
        }
    }

    @Test
    fun `사전 검사를 시작하면 기본 namespace 에 고정 이미지의 Job 을 만들고 감사 로그에 남긴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val logger = LoggerFactory.getLogger(GpuAuditLog::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        // when
        try {
            postPrecheck(setup).andExpect { status { isAccepted() } }
        } finally {
            logger.detachAppender(appender)
        }

        // then
        val job = fakeEksAccessGateway.precheckJobs.single()
        job.name.startsWith("kukie-gpu-precheck-") shouldBe true
        job.image.contains("@sha256:") shouldBe true
        job.script.contains("/connector/v1/connect") shouldBe true
        appender.list.single().formattedMessage.substringAfter("action=") shouldBe
            "precheck-start namespace=${job.namespace} name=${job.name}"
    }

    @Test
    fun `팀 관리자가 아니면 사전 검사를 시작할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)

        // when & then
        postPrecheck(setup).andExpect { status { isForbidden() } }
        fakeEksAccessGateway.precheckJobs.shouldBeEmpty()
    }

    @Test
    fun `AccessLevel 이 limited 면 사전 검사를 시작할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED)

        // when & then
        postPrecheck(setup).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.precheckJobs.shouldBeEmpty()
    }

    @Test
    fun `사전 검사가 끝나면 curl 종료 코드를 대상별 결과로 바꾼다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.precheckPod = PrecheckPodState(
            "Succeeded", null, false, """{"gateway":0,"huggingface":6,"registry":28}""",
        )

        // when & then
        getPrecheck(setup).andExpect {
            status { isOk() }
            jsonPath("$.state") { value("DONE") }
            jsonPath("$.checks[0].target") { value("gateway") }
            jsonPath("$.checks[0].status") { value("PASS") }
            jsonPath("$.checks[1].status") { value("FAIL") }
            jsonPath("$.checks[1].reason") { value("DNS") }
            jsonPath("$.checks[2].reason") { value("TIMEOUT") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"gateway":"<script>"}""", "not json", """{"gateway":0}"""])
    fun `사전 검사 결과가 약속과 다르면 믿지 않고 FAILED 다`(message: String) {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.precheckPod = PrecheckPodState("Succeeded", null, false, message)

        // when & then
        getPrecheck(setup).andExpect { jsonPath("$.state") { value("FAILED") } }
    }

    @Test
    fun `사전 검사 Pod 의 상태를 화면이 쓸 상태로 바꾼다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fun stateOf(pod: PrecheckPodState?): String {
            fakeEksAccessGateway.precheckPod = pod
            val body = getPrecheck(setup).andReturn().response.contentAsString
            return body.substringAfter("\"state\":\"").substringBefore("\"")
        }

        // when & then
        stateOf(PrecheckPodState("Pending", "ImagePullBackOff", false, null)) shouldBe "IMAGE_PULL_FAILED"
        stateOf(PrecheckPodState("Pending", null, true, null)) shouldBe "UNSCHEDULABLE"
        stateOf(PrecheckPodState("Running", null, false, null)) shouldBe "RUNNING"
        stateOf(PrecheckPodState("Failed", null, false, null)) shouldBe "FAILED"
        stateOf(null) shouldBe "EXPIRED"
    }

    @Test
    fun `사전 검사 이름이 형식에 안 맞으면 쿠버네티스를 부르지 않고 EXPIRED 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.precheckPod = PrecheckPodState("Running", null, false, null)

        // when & then
        getPrecheck(setup, "other-job").andExpect { jsonPath("$.state") { value("EXPIRED") } }
    }

    @Test
    fun `측정을 요청하면 m-epoch초 값을 annotation 에 읽은 버전과 함께 쓴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val before = Instant.now().epochSecond

        // when
        val response = postMeasure(setup, "gpt-oss-20b").andExpect { status { isOk() } }.andReturn().response.contentAsString

        // then
        val after = Instant.now().epochSecond
        val write = fakeEksAccessGateway.measureWrites.single()
        write.namespace shouldBe "llm"
        write.name shouldBe "gpt-oss-20b"
        write.resourceVersion shouldBe "7"
        write.request.startsWith("m-") shouldBe true
        (write.request.removePrefix("m-").toLong() in before..after) shouldBe true
        response shouldBe """{"request":"${write.request}"}"""
    }

    @Test
    fun `측정 요청을 감사 로그에 남긴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        val logger = LoggerFactory.getLogger(GpuAuditLog::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        // when
        try {
            postMeasure(setup, "gpt-oss-20b").andExpect { status { isOk() } }
        } finally {
            logger.detachAppender(appender)
        }

        // then
        appender.list.map { it.formattedMessage.substringAfter("action=") } shouldBe listOf(
            "measure-model namespace=llm name=gpt-oss-20b",
        )
    }

    @Test
    fun `측정 요청을 쓰는 사이 ServedModel 이 바뀌면 다시 읽어 새 버전으로 쓴다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.measureConflicts = 2
        fakeEksAccessGateway.servedModelStateAfterMeasureConflict = FakeEksAccessGateway.READY_MODEL.copy(resourceVersion = "8")

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect { status { isOk() } }
        fakeEksAccessGateway.measureWrites.single().resourceVersion shouldBe "8"
    }

    @Test
    fun `측정 요청이 충돌한 사이 측정이 시작됐으면 쓰지 않고 거절한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.measureConflicts = 1
        fakeEksAccessGateway.servedModelStateAfterMeasureConflict = FakeEksAccessGateway.READY_MODEL.copy(phase = "Measuring")

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value(ClusterErrorCode.MODEL_NOT_READY.code) }
        }
        fakeEksAccessGateway.measureWrites.shouldBeEmpty()
    }

    @Test
    fun `측정 요청이 계속 충돌하면 실패로 알린다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.measureConflicts = 3

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_CLUSTER_API_FAILED.code) }
        }
        fakeEksAccessGateway.measureWrites.shouldBeEmpty()
    }

    @Test
    fun `Ready 가 아니거나 지우는 중인 모델은 측정하지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        listOf(
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Measuring"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Loading"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = "Stopped"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = "ready"),
            FakeEksAccessGateway.READY_MODEL.copy(phase = null),
            FakeEksAccessGateway.READY_MODEL.copy(deleting = true),
        ).forEach { state ->
            fakeEksAccessGateway.servedModelState = state

            // when & then
            postMeasure(setup, "gpt-oss-20b").andExpect {
                status { isConflict() }
                jsonPath("$.code") { value(ClusterErrorCode.MODEL_NOT_READY.code) }
            }
        }
        fakeEksAccessGateway.measureWrites.shouldBeEmpty()
    }

    @Test
    fun `팀 Admin 이 아니면 쿠버네티스를 부르지 않고 측정을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL)

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `limited 면 쿠버네티스를 부르지 않고 측정을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.LIMITED)

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `쿠버네티스 이름이 아니면 쿠버네티스를 부르지 않고 측정이 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)

        // when & then
        postMeasure(setup, "Bad_Name").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
    }

    @Test
    fun `없는 모델을 측정하면 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL)
        fakeEksAccessGateway.servedModelState = null

        // when & then
        postMeasure(setup, "gpt-oss-20b").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(ClusterErrorCode.SERVED_MODEL_NOT_FOUND.code) }
        }
    }

    private fun postChat(setup: Setup, name: String, body: String = CHAT_BODY): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models/$name/chat") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun postExtend(setup: Setup, name: String, hours: Int): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models/$name/extend") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"hours":$hours}"""
        }

    private fun postPrecheck(setup: Setup): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/precheck") {
            authorization(setup.accessToken)
        }

    private fun getPrecheck(setup: Setup, name: String = "kukie-gpu-precheck-1793000000"): ResultActionsDsl =
        mockMvc.get("/clusters/${setup.clusterId}/gpu/precheck/$name") {
            authorization(setup.accessToken)
        }

    private fun postMeasure(setup: Setup, name: String): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models/$name/measure") {
            authorization(setup.accessToken)
        }

    private fun getControl(setup: Setup): ResultActionsDsl =
        mockMvc.get("/clusters/${setup.clusterId}/gpu/node-groups") {
            authorization(setup.accessToken)
        }.andExpect { status { isOk() } }

    private fun putDesired(setup: Setup, name: String, desired: Int): ResultActionsDsl =
        mockMvc.put("/clusters/${setup.clusterId}/gpu/node-groups/$name") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"desired":$desired}"""
        }

    private fun postModel(setup: Setup, model: String, storageGi: Int, gpusJson: String? = null): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            val gpus = gpusJson?.let { ""","gpus":$it""" }.orEmpty()
            content = """{"model":"$model","storageGi":$storageGi$gpus}"""
        }

    private fun deleteModel(setup: Setup, name: String): ResultActionsDsl =
        mockMvc.delete("/clusters/${setup.clusterId}/gpu/served-models/$name") {
            authorization(setup.accessToken)
        }

    private fun nodegroup(name: String, vararg instanceTypes: String) =
        EksNodegroupState(name, instanceTypes.toList(), "ACTIVE", desired = 0, min = 0, max = 2)

    /** 스택 알림으로 연결을 마친 팀에 EKS 클러스터를 하나 등록해 둔다. */
    private fun eksCluster(
        role: TeamRole,
        accessLevel: AccessLevel,
        gpuNodeControl: Boolean = false,
        gpuClusterPattern: String? = "*",
    ): Setup {
        val user = loggedInUser()
        val teamId = savedTeam(user.user.id to role)
        val connection = AwsConnection(teamId = teamId, createdBy = user.user.id, externalId = EXTERNAL_ID)
        connection.connect(ACCOUNT_ID, LocalDateTime.now(), null, accessLevel, gpuNodeControl, gpuClusterPattern)
        awsConnectionRepository.save(connection)
        val credential = EksCredential(
            accountId = ACCOUNT_ID,
            externalId = EXTERNAL_ID,
            region = DEFAULT_REGION,
            clusterName = DEFAULT_CLUSTER_NAME,
        )
        val cluster = clusterRepository.save(
            ClusterFixture.cluster(
                registeredBy = user.user.id,
                teamId = teamId,
                credential = credentialCipher.encrypt(credential.toMap()),
                provider = ClusterProvider.EKS,
                accessLevel = accessLevel,
            ),
        )
        return Setup(cluster.id, user.accessToken)
    }

    private data class Setup(
        val clusterId: UUID,
        val accessToken: String,
    )

    companion object {
        private const val ACCOUNT_ID = "123456789012"
        private const val EXTERNAL_ID = "kukie-0123456789abcdef"
        private val TARGET = AwsTarget(accountId = ACCOUNT_ID, externalId = EXTERNAL_ID)
        private const val CHAT_BODY = """{"messages":[{"role":"user","content":"안녕"}]}"""
    }
}
