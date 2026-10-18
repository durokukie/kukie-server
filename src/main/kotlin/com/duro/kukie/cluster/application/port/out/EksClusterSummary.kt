package com.duro.kukie.cluster.application.port.out

data class EksClusterSummary(
    val name: String,
    val region: String,
    val status: String,
    val version: String?,
)
