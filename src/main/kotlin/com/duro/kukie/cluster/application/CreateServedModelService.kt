package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.CreateServedModelCommand
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EngineResources
import com.duro.kukie.cluster.application.port.out.ServedModelManifest
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ServedModelExistsException
import com.duro.kukie.cluster.presentation.dto.response.ServedModelResponse
import com.duro.kukie.global.config.properties.ClusterGpuProperties
import com.duro.kukie.modelcatalog.application.ModelFavoriteService
import org.springframework.stereotype.Service

@Service
class CreateServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
    private val clusterGpuProperties: ClusterGpuProperties,
    private val modelFavoriteService: ModelFavoriteService,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: CreateServedModelCommand): ServedModelResponse {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        val access = eksGpuAccess.of(cluster)
        val name = ServedModelName.of(command.model)

        val action = "start-model namespace=$SERVING_NAMESPACE name=$name model=${command.model} storageGi=${command.storageGi} gpus=${command.gpus}"
        gpuAuditLog.record(command.userId, command.clusterId, action) {
            if (!access.serving) throw GpuServingDeniedException()
            val endpoint = eksGpuAccess.endpointOf(cluster)
            val credential = access.credential
            // 이름이 달라도 클러스터에 모델 하나다. 동시에 시작하면 둘 다 지나갈 수 있어 화면의 확인 창에 기댄다
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
                    gpus = command.gpus,
                    engineResources = engineResources(command.gpus),
                    storageGi = command.storageGi,
                    retainOnDelete = true,
                ),
            )
        }
        // 시작한 모델은 그 클러스터의 후보 즐겨찾기에 남긴다 — 측정한 모델이 다운로드 순위에서 밀려도 후보에 남게
        modelFavoriteService.rememberStarted(command.clusterId, command.userId, command.model)
        return ServedModelResponse(namespace = SERVING_NAMESPACE, name = name, model = command.model)
    }

    private fun engineResources(gpus: Int): EngineResources? =
        if (gpus < 2) {
            null
        } else {
            EngineResources(
                cpuRequest = CPU_REQUEST_PER_GPU * gpus,
                memoryRequestGi = MEMORY_REQUEST_GI_PER_GPU * gpus,
                memoryLimitGi = MEMORY_LIMIT_GI_PER_GPU * gpus,
            )
        }

    companion object {
        internal const val SERVING_NAMESPACE = "llm"

        // 장당 값은 g6e.12xlarge 4장에서 gpt-oss-120b 를 띄운 실측 값을 나눈 것이다
        private const val CPU_REQUEST_PER_GPU = 4
        private const val MEMORY_REQUEST_GI_PER_GPU = 32
        private const val MEMORY_LIMIT_GI_PER_GPU = 64
    }
}
