package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.ExtendServedModelCommand
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.exception.GpuClusterApiFailedException
import com.duro.kukie.cluster.exception.GpuServingDeniedException
import com.duro.kukie.cluster.exception.ModelNotReadyException
import com.duro.kukie.cluster.exception.ServedModelNotFoundException
import com.duro.kukie.cluster.presentation.dto.response.ExtendServedModelResponse
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ExtendServedModelService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
) {

    /** AWS·쿠버네티스 API 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: ExtendServedModelCommand): ExtendServedModelResponse {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        val access = eksGpuAccess.of(cluster)
        // 이름이 쿠버네티스 API 주소에 그대로 들어간다
        if (!ServedModelName.valid(command.name)) throw ServedModelNotFoundException()

        val namespace = CreateServedModelService.SERVING_NAMESPACE
        val action = "extend-model namespace=$namespace name=${command.name} hours=${command.hours}"
        return gpuAuditLog.record(command.userId, command.clusterId, action) {
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
                    command.name,
                )
                // 멈춘 모델은 오븐이 annotation 을 보지 않아 다시 뜨지 않는다
                if (state.phase == PHASE_STOPPED || state.deleting) throw ModelNotReadyException()
                val resourceVersion = state.resourceVersion ?: throw GpuClusterApiFailedException()

                val requested = Instant.now().plus(Duration.ofHours(command.hours.toLong())).truncatedTo(ChronoUnit.SECONDS)
                val keepUntil = listOfNotNull(requested, state.keepUntil, state.stopsAt).max()
                val written = eksAccessGateway.keepServedModelUntil(
                    credential.target,
                    credential.region,
                    credential.clusterName,
                    endpoint,
                    namespace,
                    command.name,
                    keepUntil,
                    resourceVersion,
                )
                if (written) return@record ExtendServedModelResponse(keepUntil.toString())
            }
            // 오븐의 상태 기록과 겹치면 충돌이 나서 다시 읽고 쓴다. 그래도 계속 겹치면 쿠버네티스 쪽 문제로 본다
            throw GpuClusterApiFailedException()
        }
    }

    private companion object {
        const val PHASE_STOPPED = "Stopped"
        const val WRITE_ATTEMPTS = 3
    }
}
