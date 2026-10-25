package com.duro.kukie.cluster.presentation.dto.response

import com.duro.kukie.cluster.application.StackConsoleUrl
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import java.util.UUID

/** AWS 계정 하나와의 연결. accountId 가 있으면 연결됨, lastAccountId 만 있으면 해제됨, 둘 다 없으면 스택 알림을 기다리는 중이다. */
data class AwsConnectionResponse(
    val id: UUID,
    val externalId: String,
    /** 스택 알림으로 확인한 계정. 아직이면(또는 연결을 해제했으면) null — 화면은 이 값이 생길 때까지 기다린다. */
    val accountId: String?,
    /** 연결을 해제하기 전 계정. 해제한 적이 없거나 다시 연결했으면 null. */
    val lastAccountId: String?,
    /** 이 연결의 스택을 만드는(다시 연결할 때도 같은) CloudFormation 빠른 생성 링크. */
    val quickCreateUrl: String,
    val templateUrl: String,
    val stackName: String,
    /** 연결한(또는 해제한 뒤 지울) 스택을 여는 CloudFormation 콘솔 주소. 연결한 적이 없으면 null. */
    val stackConsoleUrl: String?,
    /**
     * 연결을 해제한 뒤 고객이 스택을 지웠는지. 역할을 맡아 보고 판단한다. 연결되어 있거나, 해제한 적이 없거나,
     * 확인하지 못했으면 null.
     */
    val stackDeleted: Boolean?,
) {
    companion object {
        fun of(connection: AwsConnection, properties: ClusterAwsProperties, stackDeleted: Boolean?): AwsConnectionResponse {
            val stackName = properties.stackNameFor(connection.externalId)
            val stackConsoleUrl = when {
                connection.connected -> StackConsoleUrl.of(connection.stackId, stackName, properties.consoleRegion)
                connection.lastAccountId != null ->
                    StackConsoleUrl.of(connection.lastStackId, stackName, properties.consoleRegion)
                else -> null
            }
            return AwsConnectionResponse(
                id = connection.id,
                externalId = connection.externalId,
                accountId = connection.accountId,
                lastAccountId = connection.lastAccountId,
                quickCreateUrl = properties.quickCreateUrlFor(connection.externalId),
                templateUrl = properties.templateUrl,
                stackName = stackName,
                stackConsoleUrl = stackConsoleUrl,
                stackDeleted = stackDeleted,
            )
        }
    }
}
