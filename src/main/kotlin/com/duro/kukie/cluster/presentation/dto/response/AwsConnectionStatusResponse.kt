package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.domain.AwsConnection

data class AwsConnectionStatusResponse(
    val externalId: String,
    /** 스택 알림으로 확인한 계정. 아직이면 null. */
    val accountId: String?,
) {
    companion object {
        fun from(connection: AwsConnection) =
            AwsConnectionStatusResponse(externalId = connection.externalId, accountId = connection.accountId)
    }
}
