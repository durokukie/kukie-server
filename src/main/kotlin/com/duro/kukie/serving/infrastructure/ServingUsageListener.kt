package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.logger
import com.duro.kukie.serving.application.CompleteServingUsageService
import com.duro.kukie.serving.application.ServingUsageObservation
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ServingUsageListener(private val complete: CompleteServingUsageService) {
    private val log = logger()
    @org.springframework.core.annotation.Order(0)
    @EventListener
    fun onUsage(event: ServingUsageObservation) {
        // 중계 취소 인터럽트 때문에 DB 연결 대기가 즉시 실패하지 않게 한다.
        val interrupted = Thread.interrupted()
        try { complete(event) }
        catch (_: Exception) { log.warn("API 사용량 완료 기록 실패: 시작 기록은 유지되며 미완료로 표시합니다.") }
        finally { if (interrupted) Thread.currentThread().interrupt() }
    }
}
