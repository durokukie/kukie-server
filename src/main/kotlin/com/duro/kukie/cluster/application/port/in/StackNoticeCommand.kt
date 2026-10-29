package com.duro.kukie.cluster.application.port.`in`

/**
 * 템플릿 v2 스택의 사용자 지정 리소스(KukieNotice)가 보낸 CloudFormation 요청. 보관함(SQS)에서 꺼낸 그대로다 —
 * 누구나 보낼 수 있으므로 믿지 않고 확인한다.
 */
data class StackNoticeCommand(
    val requestType: String,
    val responseUrl: String,
    val stackId: String,
    val requestId: String,
    val logicalResourceId: String,
    val physicalResourceId: String?,
    val externalId: String?,
    val roleArn: String?,
    /** 템플릿 v5 의 AccessLevel(full·limited). v1~v4 스택은 보내지 않는다. */
    val accessLevel: String?,
) {
    companion object {
        const val CREATE = "Create"
        const val UPDATE = "Update"
        const val DELETE = "Delete"
    }
}
