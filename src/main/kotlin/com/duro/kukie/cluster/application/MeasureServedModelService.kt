package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ModelNotReadyException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.cluster.presentation.dto.response.MeasureServedModelResponse
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class MeasureServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID, name: String): MeasureServedModelResponse {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        val access = eksGpuAccess.of(cluster)
        // 이름이 쿠버네티스 API 주소에 그대로 들어간다
        if (!ServedModelName.valid(name)) throw ServedModelNotFoundException()

        val namespace = CreateServedModelService.SERVING_NAMESPACE
        return gpuAuditLog.record(userId, clusterId, "measure-model namespace=$namespace name=$name") {
            if (!access.serving) throw GpuServingDeniedException()
            val credential = access.credential
            val endpoint = eksGpuAccess.endpointOf(cluster)
            repeat(WRITE_ATTEMPTS) {
                val state = eksAccessGateway.getServedModel(
                    credential.target,
                    credential.region,
                    credential.clusterName,
                    endpoint,
                    namespace,
                    name,
                )
                // 측정은 GPU 를 가득 채우므로 요청을 받을 수 있는 모델에만 건다. 이미 측정 중이면 Measuring 이라 여기서 걸러진다
                if (state.phase != PHASE_READY || state.deleting) throw ModelNotReadyException()
                val resourceVersion = state.resourceVersion ?: throw GpuClusterApiFailedException()

                val request = "$REQUEST_PREFIX${Instant.now().epochSecond}"
                val written = eksAccessGateway.requestServedModelMeasure(
                    credential.target,
                    credential.region,
                    credential.clusterName,
                    endpoint,
                    namespace,
                    name,
                    request,
                    resourceVersion,
                )
                if (written) return@record MeasureServedModelResponse(request)
            }
            // 오븐의 상태 기록과 겹치면 충돌이 나서 다시 읽고 쓴다. 그래도 계속 겹치면 쿠버네티스 쪽 문제로 본다
            throw GpuClusterApiFailedException()
        }
    }

    private companion object {
        const val PHASE_READY = "Ready"
        const val REQUEST_PREFIX = "m-"
        const val WRITE_ATTEMPTS = 3
    }
}
