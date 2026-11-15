package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(clusterId: UUID, userId: UUID, name: String) {
        val cluster = clusterPermission.requireWritable(clusterId, userId)
        val access = eksGpuAccess.of(cluster)
        // 이름이 쿠버네티스 API 주소에 그대로 들어간다
        if (!ServedModelName.valid(name)) throw ServedModelNotFoundException()

        val action = "stop-model namespace=${CreateServedModelService.SERVING_NAMESPACE} name=$name"
        gpuAuditLog.record(userId, clusterId, action) {
            if (!access.serving) throw GpuServingDeniedException()
            val credential = access.credential
            eksAccessGateway.deleteServedModel(
                credential.target,
                credential.region,
                credential.clusterName,
                eksGpuAccess.endpointOf(cluster),
                CreateServedModelService.SERVING_NAMESPACE,
                name,
            )
        }
    }
}
