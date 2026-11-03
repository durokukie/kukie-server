package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.CreateServedModelCommand
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.ServedModelManifest
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.presentation.dto.response.ServedModelResponse
import com.duro.kukie.global.config.properties.ClusterGpuProperties
import org.springframework.stereotype.Service

@Service
class CreateServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
    private val clusterGpuProperties: ClusterGpuProperties,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: CreateServedModelCommand): ServedModelResponse {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        val access = eksGpuAccess.of(cluster)
        val name = ServedModelName.of(command.model)

        val action = "start-model namespace=$SERVING_NAMESPACE name=$name model=${command.model} storageGi=${command.storageGi}"
        gpuAuditLog.record(command.userId, command.clusterId, action) {
            if (!access.control) throw GpuServingDeniedException()
            val endpoint = eksGpuAccess.endpointOf(cluster)
            val credential = access.credential
            // 이름이 달라도 GPU 한 장에 모델 하나다. 동시에 시작하면 둘 다 지나갈 수 있어 화면의 확인 창에 기댄다
            if (eksAccessGateway.hasServedModel(credential.target, credential.region, credential.clusterName, endpoint, SERVING_NAMESPACE)) {
                throw ServedModelExistsException()
            }
            eksAccessGateway.createServedModel(
                credential.target,
                credential.region,
                credential.clusterName,
                endpoint,
                ServedModelManifest(
                    namespace = SERVING_NAMESPACE,
                    name = name,
                    model = command.model,
                    engineImage = clusterGpuProperties.engineImage,
                    engineArgs = clusterGpuProperties.engineArgs,
                    gpus = ENGINE_GPUS,
                    storageGi = command.storageGi,
                    retainOnDelete = true,
                ),
            )
        }
        return ServedModelResponse(namespace = SERVING_NAMESPACE, name = name, model = command.model)
    }

    companion object {
        internal const val SERVING_NAMESPACE = "llm"
        private const val ENGINE_GPUS = 1
    }
}
