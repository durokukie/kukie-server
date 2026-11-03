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
import com.duro.kukie.cluster.exception.NodeGroupBusyException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
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
    fun `패턴 밖 클러스터면 쿠버네티스를 부르지 않고 모델 시작과 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true, gpuClusterPattern = "other-*")

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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

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
    fun `팀 Admin 이 아니면 모델을 시작할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `namespace 에 다른 ServedModel 이 있으면 만들지 않는다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
    fun `v5 full 스택이면 쿠버네티스를 부르지 않고 모델 시작을 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = false)

        // when & then
        postModel(setup, "openai/gpt-oss-20b", 40).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeEksAccessGateway.servedModelCreations.shouldBeEmpty()
    }

    @Test
    fun `연결을 해제했으면 쿠버네티스를 부르지 않고 모델 시작과 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
    fun `모델 형식이 CRD 패턴과 다르거나 저장 공간이 범위를 벗어나면 400 이다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        listOf(20, 500).forEach { storageGi ->
            postModel(setup, "openai/gpt-oss-20b", storageGi).andExpect { status { isCreated() } }
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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect { status { isNoContent() } }
        fakeEksAccessGateway.servedModelDeletions shouldBe listOf(
            FakeEksAccessGateway.ServedModelDeletion("llm", "gpt-oss-20b"),
        )
    }

    @Test
    fun `팀 Admin 이 아니면 모델을 중지할 수 없다`() {
        // given
        val setup = eksCluster(TeamRole.MEMBER, AccessLevel.FULL, gpuNodeControl = true)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(TeamErrorCode.NOT_TEAM_ADMIN.code) }
        }
    }

    @Test
    fun `없는 ServedModel 을 지우면 404 다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)
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
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = true)

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
    fun `v5 full 스택이면 쿠버네티스를 부르지 않고 모델 중지를 거부한다`() {
        // given
        val setup = eksCluster(TeamRole.ADMIN, AccessLevel.FULL, gpuNodeControl = false)

        // when & then
        deleteModel(setup, "gpt-oss-20b").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value(ClusterErrorCode.GPU_SERVING_DENIED.code) }
        }
        fakeEksAccessGateway.targets.shouldBeEmpty()
        fakeEksAccessGateway.servedModelDeletions.shouldBeEmpty()
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

    private fun postModel(setup: Setup, model: String, storageGi: Int): ResultActionsDsl =
        mockMvc.post("/clusters/${setup.clusterId}/gpu/served-models") {
            authorization(setup.accessToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"model":"$model","storageGi":$storageGi}"""
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
    }
}
