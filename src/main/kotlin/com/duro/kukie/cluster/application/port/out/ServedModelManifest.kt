package com.duro.kukie.cluster.application.port.out

data class ServedModelManifest(
    val namespace: String,
    val name: String,
    val model: String,
    val engineImage: String,
    val engineArgs: List<String>,
    val gpus: Int,
    val storageGi: Int,
    val retainOnDelete: Boolean,
)
