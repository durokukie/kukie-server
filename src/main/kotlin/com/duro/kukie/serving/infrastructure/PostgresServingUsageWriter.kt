package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.ServingUsageObservation
import com.duro.kukie.serving.application.port.out.ServingUsageWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PostgresServingUsageWriter(private val jdbc: JdbcTemplate) : ServingUsageWriter {
    override fun complete(event: ServingUsageObservation) {
        val outcome = event.outcome.uppercase().takeIf { it in OUTCOMES } ?: "FAILED"
        jdbc.update("""
            update tbl_serving_usage set finished_at = ?, updated_at = ?, duration_millis = ?, http_status = ?, wire_status = ?,
                outcome = ?, input_tokens = ?, output_tokens = ?, usage_missing = ?
            where id = ? and key_id = ? and access_id = ? and cluster_id = ? and user_id = ? and outcome = 'IN_PROGRESS'
        """.trimIndent(), utcNow(), utcNow(), event.durationMillis.coerceAtLeast(0), event.httpStatus, event.wireStatus,
            outcome, event.inputTokens?.takeIf { it >= 0 }, event.outputTokens?.takeIf { it >= 0 }, event.usageMissing,
            event.requestId, event.identity.keyId, event.identity.accessId, event.identity.clusterId, event.identity.userId)
    }
    private companion object { val OUTCOMES = setOf("COMPLETED", "FAILED", "TIMEOUT", "CANCELLED", "INCOMPLETE") }
}
