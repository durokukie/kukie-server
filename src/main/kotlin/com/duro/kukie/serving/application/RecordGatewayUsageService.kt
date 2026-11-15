package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.out.ServingUsageWriter
import com.duro.kukie.serving.presentation.dto.request.GatewayRejectionRecord
import com.duro.kukie.serving.presentation.dto.request.GatewayUsageRecord
import com.duro.kukie.serving.presentation.dto.request.GatewayUsageRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class RecordGatewayUsageService(private val writer: ServingUsageWriter, private val properties: ServingRelayProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    operator fun invoke(request: GatewayUsageRequest) {
        val records = request.records.orEmpty().filterNotNull()
        val now = utcNow().toInstant(ZoneOffset.UTC)
        val oldest = now.minus(MAX_AGE)
        val latest = now.plus(MAX_SKEW)
        val rejections = request.rejections.orEmpty().filterNotNull()
        fun Instant.inWindow() = !isBefore(oldest) && !isAfter(latest)
        val inWindow = records.filter { it.startedAt.inWindow() }
        val rejectionsInWindow = rejections.map { it.copy(minute = it.minute.truncatedTo(ChronoUnit.MINUTES)) }.filter { it.minute.inWindow() }
        val identities = writer.identities((inWindow.map { it.keyId } + rejectionsInWindow.map { it.keyId }).toSet())
        val events = inWindow.mapNotNull { record -> identities[record.keyId]?.let { record.toObservation(it) } }
        if (events.isNotEmpty()) writer.insertFinished(events, properties.totalSeconds + 60)
        val skipped = records.size - events.size
        if (skipped > 0) log.warn("serving gateway usage batch skipped {} of {} records (unknown key or out-of-window start)", skipped, records.size)
        val rejected = rejectionsInWindow.mapNotNull { r -> identities[r.keyId]?.let { r.toObservation(it) } }
        if (rejected.isNotEmpty()) writer.insertRejections(rejected)
        val skippedRejections = rejections.size - rejected.size
        if (skippedRejections > 0) log.warn("serving gateway usage batch skipped {} of {} rejections (unknown key or out-of-window minute)", skippedRejections, rejections.size)
    }

    private fun GatewayRejectionRecord.toObservation(identity: AuthenticatedServingKey) = ServingRejectionObservation(id, identity, model, reason, minute, count)

    private fun GatewayUsageRecord.toObservation(identity: AuthenticatedServingKey): ServingUsageObservation {
        val known = tokensKnown && inputTokens != null && outputTokens != null
        return ServingUsageObservation(
            identity, model, startedAt, durationMillis,
            status.takeIf { it >= 100 } ?: 502, outcome,
            inputTokens.takeIf { known }, outputTokens.takeIf { known },
            model != null && !known, requestId, status.takeIf { it >= 100 }, ttfbMillis,
        )
    }

    private companion object {
        val MAX_AGE: Duration = Duration.ofDays(1)
        val MAX_SKEW: Duration = Duration.ofMinutes(5)
    }
}
