package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.application.port.out.EksNodegroupState

data class GpuNodeGroupResponse(
    val name: String,
    val instanceTypes: List<String>,
    val status: String,
    val desired: Int,
    val min: Int,
    val max: Int,
) {
    companion object {
        fun from(state: EksNodegroupState) = GpuNodeGroupResponse(
            name = state.name,
            instanceTypes = state.instanceTypes,
            status = state.status,
            desired = state.desired,
            min = state.min,
            max = state.max,
        )
    }
}
