package com.duro.kukie.cluster.presentation.dto.response

data class AwsDisconnectionResponse(
    /** 고객이 KukieAccess 스택을 지울 CloudFormation 콘솔 주소. */
    val stackConsoleUrl: String,
    val stackName: String,
)
