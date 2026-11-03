package com.duro.kukie.cluster.presentation.dto.response

data class ServedModelResponse(
    val namespace: String,
    val name: String,
    val model: String,
)
