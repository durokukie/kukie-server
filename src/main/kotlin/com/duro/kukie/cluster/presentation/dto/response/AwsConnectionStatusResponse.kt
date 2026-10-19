package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.domain.AwsConnection

data class AwsConnectionStatusResponse(
    val externalId: String,
    /** 스택 알림으로 확인한 계정. 아직이면(또는 연결을 해제했으면) null. */
    val accountId: String?,
    /** 연결한(또는 해제한 뒤 지울) 스택을 여는 CloudFormation 콘솔 주소. 연결한 적이 없으면 null. */
    val stackConsoleUrl: String?,
    /** 그 스택의 이름. stackConsoleUrl 이 있을 때만 준다. */
    val stackName: String?,
    /**
     * 연결을 해제한 뒤 고객이 스택을 지웠는지. 역할을 맡아 보고 판단한다. 연결되어 있거나, 해제한 적이 없거나,
     * 확인하지 못했으면 null.
     */
    val stackDeleted: Boolean?,
) {
    companion object {
        fun from(connection: AwsConnection, stackName: String?, stackConsoleUrl: String?, stackDeleted: Boolean?) =
            AwsConnectionStatusResponse(
                externalId = connection.externalId,
                accountId = connection.accountId,
                stackConsoleUrl = stackConsoleUrl,
                stackName = stackName,
                stackDeleted = stackDeleted,
            )
    }
}
