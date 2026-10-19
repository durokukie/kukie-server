package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.domain.AwsConnection

data class AwsConnectionStatusResponse(
    val externalId: String,
    /** 스택 알림으로 확인한 계정. 아직이면(또는 연결을 해제했으면) null. */
    val accountId: String?,
    /** 연결한 스택을 여는 CloudFormation 콘솔 주소. 연결되지 않았으면 null. */
    val stackConsoleUrl: String?,
) {
    companion object {
        fun from(connection: AwsConnection, stackConsoleUrl: String?) = AwsConnectionStatusResponse(
            externalId = connection.externalId,
            accountId = connection.accountId,
            stackConsoleUrl = stackConsoleUrl,
        )
    }
}
