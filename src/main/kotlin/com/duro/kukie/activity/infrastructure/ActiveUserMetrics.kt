package com.duro.kukie.activity.infrastructure

import com.duro.kukie.activity.domain.ActiveUserRepository
import com.duro.kukie.activity.domain.ActiveWindow
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.ObservableLongGauge
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.time.Clock

/** 값은 Redis 에서 읽어 replica 마다 같으므로 대시보드는 sum 이 아니라 max 로 합친다. */
@Component
class ActiveUserMetrics(
    private val activeUserRepository: ActiveUserRepository,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val clock: Clock = Clock.systemUTC()
    private val windowKey = AttributeKey.stringKey("window")

    private val gauge: ObservableLongGauge = GlobalOpenTelemetry.getMeter("kukie")
        .gaugeBuilder("kukie.users.active")
        .setDescription("최근 창 안에 인증된 요청을 한 번이라도 보낸 사용자 수")
        .setUnit("{user}")
        .ofLongs()
        .buildWithCallback { measurement ->
            val now = clock.instant()
            ActiveWindow.entries.forEach { window ->
                runCatching { activeUserRepository.count(window, now) }
                    .onSuccess { measurement.record(it, Attributes.of(windowKey, window.label)) }
                    .onFailure { log.warn("active user count failed: {}", it.message) }
            }
        }

    override fun destroy() = gauge.close()
}
