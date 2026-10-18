package com.duro.kukie.support

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.StackNoticeResponder
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import com.duro.kukie.cluster.infrastructure.CloudFormationResponseUrl

/** 회신을 보내지 않고 기록한다. 응답 주소 판단은 실제 규칙을 그대로 쓴다. */
class FakeStackNoticeResponder : StackNoticeResponder, Resettable {

    val responses = mutableListOf<StackNoticeResult>()

    override fun accepts(responseUrl: String): Boolean = CloudFormationResponseUrl.matches(responseUrl)

    override fun respond(notice: StackNoticeCommand, result: StackNoticeResult) {
        responses += result
    }

    override fun clear() {
        responses.clear()
    }
}
