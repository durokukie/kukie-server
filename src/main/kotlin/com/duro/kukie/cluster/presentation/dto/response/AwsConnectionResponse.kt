package com.duro.kukie.cluster.presentation.dto.response

data class AwsConnectionResponse(
    val externalId: String,
    val quickCreateUrl: String,
    val templateUrl: String,
    val stackName: String,
    /** 스택 알림으로 확인한 계정. 아직이면 null — 화면은 이 값이 생길 때까지 기다린다. */
    val accountId: String?,
)
