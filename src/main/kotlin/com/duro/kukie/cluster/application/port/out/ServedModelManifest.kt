package com.duro.kukie.cluster.application.port.out

data class ServedModelManifest(
    val namespace: String,
    val name: String,
    val model: String,
    val engineImage: String,
    val engineArgs: List<String>,
    val gpus: Int,
    /** null 이면 본문에 넣지 않아 오븐 기본값을 쓴다. */
    val engineResources: EngineResources?,
    val storageGi: Int,
    val retainOnDelete: Boolean,
)

data class EngineResources(
    val cpuRequest: Int,
    val memoryRequestGi: Int,
    val memoryLimitGi: Int,
)
