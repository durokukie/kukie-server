package com.duro.kukie.cluster.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.util.logger
import org.springframework.stereotype.Component
import java.util.UUID

/** 화면의 확인 창을 승인으로 보므로 누가 무엇을 했는지 서버 로그에 남긴다. action 에는 검사를 마친 값만 넣는다. */
@Component
class GpuAuditLog {

    private val log = logger()

    fun <T> record(userId: UUID, clusterId: UUID, action: String, block: () -> T): T {
        val result = try {
            block()
        } catch (exception: RuntimeException) {
            val reason = (exception as? BusinessException)?.errorCode?.code ?: exception.javaClass.simpleName
            log.warn("GPU 조작 실패: user={} cluster={} action={} reason={}", userId, clusterId, action, reason)
            throw exception
        }
        log.info("GPU 조작 완료: user={} cluster={} action={}", userId, clusterId, action)
        return result
    }
}
