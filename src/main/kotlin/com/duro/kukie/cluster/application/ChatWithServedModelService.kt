package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.ModelChatCommand
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ModelNotReadyException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper

@Service
class ChatWithServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val modelChatRelay: ModelChatRelay,
    private val gpuAuditLog: GpuAuditLog,
) {

    private val jsonMapper = JsonMapper.builder().build()

    /**
     * 시작하기 전에 알 수 있는 거절은 예외(일반 JSON 오류)로, 시작한 뒤의 실패는 SSE error 이벤트로 알린다.
     * AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다.
     */
    operator fun invoke(command: ModelChatCommand): SseEmitter {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        val access = eksGpuAccess.of(cluster)
        // 이름이 쿠버네티스 API 주소에 그대로 들어간다
        if (!ServedModelName.valid(command.name)) throw ServedModelNotFoundException()

        val namespace = CreateServedModelService.SERVING_NAMESPACE
        val action = "start-chat namespace=$namespace name=${command.name} maxTokens=${command.maxTokens}"
        return gpuAuditLog.record(command.userId, command.clusterId, action) {
            if (!access.serving) throw GpuServingDeniedException()
            val credential = access.credential
            val endpoint = eksGpuAccess.endpointOf(cluster)
            val state = eksAccessGateway.getServedModel(
                credential.target,
                credential.region,
                credential.clusterName,
                endpoint,
                namespace,
                command.name,
            )
            if (state.phase != PHASE_READY || state.deleting) throw ModelNotReadyException()
            val model = state.model?.takeIf(ServedModelName::validModel) ?: throw GpuClusterApiFailedException()

            modelChatRelay.start(
                ModelChatTarget(
                    userId = command.userId,
                    clusterId = command.clusterId,
                    awsTarget = credential.target,
                    region = credential.region,
                    clusterName = credential.clusterName,
                    endpoint = endpoint,
                    namespace = namespace,
                    name = command.name,
                    model = model,
                ),
                requestBody(model, command),
            )
        }
    }

    private fun requestBody(model: String, command: ModelChatCommand): ByteArray =
        jsonMapper.writeValueAsBytes(
            buildMap {
                put("model", model)
                put("messages", command.messages.map { mapOf("role" to it.role, "content" to it.content) })
                put("max_tokens", command.maxTokens)
                command.temperature?.let { put("temperature", it) }
                put("stream", true)
                put("stream_options", mapOf("include_usage" to true))
            },
        )

    private companion object {
        const val PHASE_READY = "Ready"
    }
}
