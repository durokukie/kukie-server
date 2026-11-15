package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.UpdateGpuNodeGroupCommand
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.EksNodegroupState
import com.duro.kukie.cluster.exception.EksAccessDeniedException
import com.duro.kukie.cluster.exception.GpuNodeControlDeniedException
import com.duro.kukie.cluster.exception.GpuNodeGroupNotFoundException
import com.duro.kukie.cluster.exception.InvalidNodeCountException
import com.duro.kukie.cluster.presentation.dto.response.GpuNodeGroupResponse
import org.springframework.stereotype.Service

@Service
class UpdateGpuNodeGroupService(
    private val clusterPermission: ClusterPermission,
    private val eksGpuAccess: EksGpuAccess,
    private val eksAccessGateway: EksAccessGateway,
    private val gpuAuditLog: GpuAuditLog,
) {

    /** AWS 를 부르는 동안 DB 연결을 쥐지 않게 트랜잭션을 걸지 않는다. */
    operator fun invoke(command: UpdateGpuNodeGroupCommand): GpuNodeGroupResponse {
        val cluster = clusterPermission.requireWritable(command.clusterId, command.userId)
        val access = eksGpuAccess.of(cluster)
        if (!NODE_GROUP_NAME.matches(command.name)) throw GpuNodeGroupNotFoundException()

        val action = "scale-node-group name=${command.name} desired=${command.desired}"
        return gpuAuditLog.record(command.userId, command.clusterId, action) {
            if (!access.control) throw GpuNodeControlDeniedException()
            GpuNodeGroupResponse.from(update(access.credential, command))
        }
    }

    private fun update(credential: EksCredential, command: UpdateGpuNodeGroupCommand): EksNodegroupState {
        val current = read(credential, command.name)
        if (command.desired !in current.min..current.max) throw InvalidNodeCountException()
        if (command.desired == current.desired) return current

        eksAccessGateway.updateNodegroupDesired(
            credential.target,
            credential.region,
            credential.clusterName,
            current,
            command.desired,
        )
        return current.copy(desired = command.desired, status = EksNodegroupState.UPDATING)
    }

    private fun read(credential: EksCredential, name: String): EksNodegroupState {
        val state = try {
            eksAccessGateway.describeNodegroupState(credential.target, credential.region, credential.clusterName, name)
        } catch (_: EksAccessDeniedException) {
            throw GpuNodeControlDeniedException()
        }
        return state?.takeIf { it.gpu } ?: throw GpuNodeGroupNotFoundException()
    }

    companion object {
        private val NODE_GROUP_NAME = Regex("^[0-9A-Za-z][0-9A-Za-z_-]{0,62}$")
    }
}
