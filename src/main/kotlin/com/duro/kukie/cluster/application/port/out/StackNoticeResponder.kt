package com.duro.kukie.cluster.application.port.out

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand

/** CloudFormation 에 사용자 지정 리소스 결과를 회신한다. 회신이 없으면 고객 스택이 한 시간 기다리다 실패한다. */
interface StackNoticeResponder {

    /** CloudFormation 의 응답 주소(S3)인가. 아니면 회신하지 않는다 — 가짜 알림이 서버를 엉뚱한 곳으로 부르지 못하게. */
    fun accepts(responseUrl: String): Boolean

    fun respond(notice: StackNoticeCommand, result: StackNoticeResult)
}

data class StackNoticeResult(
    val success: Boolean,
    val physicalResourceId: String,
    val reason: String,
)
