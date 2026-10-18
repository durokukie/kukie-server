package com.duro.kukie.cluster.presentation.dto.response

data class AwsConnectionResponse(
    val externalId: String,
    val quickCreateUrl: String,
    val templateUrl: String,
    val stackName: String,
)
