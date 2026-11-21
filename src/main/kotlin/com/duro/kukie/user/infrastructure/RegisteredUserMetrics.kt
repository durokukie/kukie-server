package com.duro.kukie.user.infrastructure

import com.duro.kukie.user.domain.RegisteredUserCounts
import com.duro.kukie.user.domain.UserRepository
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RegisteredUserMetrics(private val userRepository: UserRepository) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = AttributeKey.stringKey("scope")
    private val current = Attributes.of(scope, "current")
    private val cumulative = Attributes.of(scope, "cumulative")

    @Volatile
    private var counts: RegisteredUserCounts? = null

    private val gauge = GlobalOpenTelemetry.getMeter("kukie").gaugeBuilder("kukie.users.registered")
        .setDescription("현재 회원 수와 탈퇴 계정을 포함한 누적 가입 계정 수")
        .setUnit("{user}")
        .ofLongs()
        .buildWithCallback { measurement ->
            counts?.let { snapshot ->
                measurement.record(snapshot.current, current)
                measurement.record(snapshot.cumulative, cumulative)
            }
        }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun refresh() {
        counts = try {
            userRepository.registeredCounts()
        } catch (e: DataAccessException) {
            log.warn("registered user count failed", e)
            null
        }
    }

    override fun destroy() = gauge.close()
}
