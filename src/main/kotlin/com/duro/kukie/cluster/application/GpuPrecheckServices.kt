package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.PrecheckJobManifest
import com.duro.kukie.cluster.application.port.out.PrecheckPodState
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.presentation.dto.response.GpuPrecheckCheck
import com.duro.kukie.cluster.presentation.dto.response.GpuPrecheckResponse
import com.duro.kukie.cluster.presentation.dto.response.GpuPrecheckStartResponse
import com.duro.kukie.global.config.properties.ClusterGpuProperties
import com.duro.kukie.global.config.properties.WebAppProperties
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** 애드온을 깔기 전에 고객 클러스터 안에서 바깥으로 나갈 수 있는지 보는 임시 Job 을 띄운다. 사용자가 눌렀을 때만 부른다. */
@Service
class StartGpuPrecheckService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
    private val clusterGpuProperties: ClusterGpuProperties,
    private val webAppProperties: WebAppProperties,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID): GpuPrecheckStartResponse {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        val access = eksGpuAccess.of(cluster)
        val namespace = cluster.defaultNamespace
        val name = "$PRECHECK_JOB_PREFIX${Instant.now().epochSecond}"

        gpuAuditLog.record(userId, clusterId, "precheck-start namespace=$namespace name=$name") {
            if (!access.serving) throw GpuServingDeniedException()
            val credential = access.credential
            eksAccessGateway.createPrecheckJob(
                credential.target,
                credential.region,
                credential.clusterName,
                eksGpuAccess.endpointOf(cluster),
                PrecheckJobManifest(namespace, name, clusterGpuProperties.precheckImage, script()),
            )
        }
        return GpuPrecheckStartResponse(name)
    }

    /** 대상마다 curl 종료 코드만 남긴다. HTTP 응답이 오면 401·404 여도 그 경로가 열린 것이라 0 이다. */
    private fun script(): String {
        val gateway = "${webAppProperties.url.trimEnd('/')}/connector/v1/connect"
        return """
            check() { curl -sS -o /dev/null --connect-timeout 5 --max-time 15 "${'$'}1" 2>/dev/null; echo ${'$'}?; }
            g=${'$'}(check '$gateway')
            h=${'$'}(check 'https://huggingface.co/api/models?limit=1')
            r=${'$'}(check 'https://ghcr.io/v2/')
            printf '{"gateway":%s,"huggingface":%s,"registry":%s}' "${'$'}g" "${'$'}h" "${'$'}r" > /dev/termination-log
        """.trimIndent()
    }
}

@Service
class GetGpuPrecheckService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val jsonMapper: JsonMapper,
) {

    operator fun invoke(clusterId: UUID, userId: UUID, name: String): GpuPrecheckResponse {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        val access = eksGpuAccess.of(cluster)
        if (!access.serving) throw GpuServingDeniedException()
        // 이름이 쿠버네티스 API 주소에 그대로 들어간다
        if (!PRECHECK_JOB_NAME.matches(name)) return GpuPrecheckResponse(STATE_EXPIRED)

        val credential = access.credential
        val pod = eksAccessGateway.getPrecheckPod(
            credential.target,
            credential.region,
            credential.clusterName,
            eksGpuAccess.endpointOf(cluster),
            cluster.defaultNamespace,
            name,
        ) ?: return GpuPrecheckResponse(STATE_EXPIRED)
        return resultOf(pod)
    }

    private fun resultOf(pod: PrecheckPodState): GpuPrecheckResponse = when {
        pod.phase == PHASE_SUCCEEDED -> parse(pod.terminationMessage)
        pod.phase == PHASE_FAILED -> GpuPrecheckResponse(STATE_FAILED)
        pod.waitingReason in IMAGE_PULL_REASONS -> GpuPrecheckResponse(STATE_IMAGE_PULL_FAILED)
        pod.unschedulable -> GpuPrecheckResponse(STATE_UNSCHEDULABLE)
        else -> GpuPrecheckResponse(STATE_RUNNING)
    }

    /** 고객 클러스터의 Pod 가 쓴 값이다. 정해진 대상의 숫자만 읽고 글자는 화면으로 넘기지 않는다. */
    private fun parse(message: String?): GpuPrecheckResponse {
        val root = try {
            jsonMapper.readTree(message ?: return GpuPrecheckResponse(STATE_FAILED))
        } catch (_: JacksonException) {
            return GpuPrecheckResponse(STATE_FAILED)
        }
        val checks = PRECHECK_TARGETS.map { target ->
            val code = root.path(target).takeIf { it.isInt }?.asInt() ?: return GpuPrecheckResponse(STATE_FAILED)
            GpuPrecheckCheck(target, if (code == 0) STATUS_PASS else STATUS_FAIL, reasonOf(code))
        }
        return GpuPrecheckResponse(STATE_DONE, checks)
    }

    /** curl 종료 코드. https://curl.se/libcurl/c/libcurl-errors.html */
    private fun reasonOf(code: Int): String? = when (code) {
        0 -> null
        5, 6 -> "DNS"
        7 -> "CONNECT"
        28 -> "TIMEOUT"
        35, 51, 58, 60, 77 -> "TLS"
        else -> "OTHER"
    }

    private companion object {
        const val PHASE_SUCCEEDED = "Succeeded"
        const val PHASE_FAILED = "Failed"
        const val STATE_RUNNING = "RUNNING"
        const val STATE_DONE = "DONE"
        const val STATE_FAILED = "FAILED"
        const val STATE_EXPIRED = "EXPIRED"
        const val STATE_IMAGE_PULL_FAILED = "IMAGE_PULL_FAILED"
        const val STATE_UNSCHEDULABLE = "UNSCHEDULABLE"
        const val STATUS_PASS = "PASS"
        const val STATUS_FAIL = "FAIL"
        val IMAGE_PULL_REASONS = setOf("ErrImagePull", "ImagePullBackOff", "InvalidImageName")
        val PRECHECK_TARGETS = listOf("gateway", "huggingface", "registry")
    }
}

private const val PRECHECK_JOB_PREFIX = "kukie-gpu-precheck-"
private val PRECHECK_JOB_NAME = Regex("$PRECHECK_JOB_PREFIX[0-9]{1,12}")
