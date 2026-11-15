package com.duro.kukie.cluster.presentation.dto.response

data class GpuNodeGroupsResponse(
    val supported: Boolean,
    val control: Boolean,
    val nodeGroups: List<GpuNodeGroupResponse>,
) {
    companion object {
        fun unsupported() = GpuNodeGroupsResponse(supported = false, control = false, nodeGroups = emptyList())
    }
}
