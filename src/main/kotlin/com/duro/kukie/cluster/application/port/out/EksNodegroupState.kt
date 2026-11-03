package com.duro.kukie.cluster.application.port.out

data class EksNodegroupState(
    val name: String,
    val instanceTypes: List<String>,
    val status: String,
    val desired: Int,
    val min: Int,
    val max: Int,
) {
    val gpu: Boolean
        get() = instanceTypes.any(GPU_INSTANCE_TYPE::containsMatchIn)

    companion object {
        const val UPDATING = "UPDATING"
        private val GPU_INSTANCE_TYPE = Regex("^(g|p)\\d")
    }
}
