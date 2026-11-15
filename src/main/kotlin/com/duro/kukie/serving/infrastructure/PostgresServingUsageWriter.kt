package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.AuthenticatedServingKey
import com.duro.kukie.serving.application.ServingRejectionObservation
import com.duro.kukie.serving.application.ServingUsageObservation
import com.duro.kukie.serving.application.port.out.ServingUsageWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Types
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresServingUsageWriter(private val jdbc: JdbcTemplate) : ServingUsageWriter {
    override fun complete(event: ServingUsageObservation) {
        jdbc.update("""
            update tbl_serving_usage set finished_at = ?, updated_at = ?, duration_millis = ?, http_status = ?, wire_status = ?,
                outcome = ?, input_tokens = ?, output_tokens = ?, usage_missing = ?
            where id = ? and key_id = ? and access_id = ? and cluster_id = ? and user_id = ? and outcome = 'IN_PROGRESS'
        """.trimIndent(), utcNow(), utcNow(), event.durationMillis.coerceAtLeast(0), event.httpStatus, event.wireStatus,
            normalize(event.outcome), event.inputTokens?.takeIf { it >= 0 }, event.outputTokens?.takeIf { it >= 0 }, event.usageMissing,
            event.requestId, event.identity.keyId, event.identity.accessId, event.identity.clusterId, event.identity.userId)
    }

    override fun identities(keyIds: Collection<UUID>): Map<UUID, AuthenticatedServingKey> {
        if (keyIds.isEmpty()) return emptyMap()
        return jdbc.query({ connection ->
            connection.prepareStatement("""
                select k.id as key_id, a.id as access_id, a.cluster_id, a.user_id
                from tbl_serving_api_key k join tbl_serving_access a on a.id = k.access_id
                where k.id = any(?) and a.user_id is not null
            """.trimIndent()).apply { setArray(1, connection.createArrayOf("uuid", keyIds.toTypedArray())) }
        }) { rs, _ ->
            AuthenticatedServingKey(rs.getObject("key_id", UUID::class.java), rs.getObject("access_id", UUID::class.java),
                rs.getObject("cluster_id", UUID::class.java), rs.getObject("user_id", UUID::class.java))
        }.associateBy { it.keyId }
    }

    override fun insertFinished(events: List<ServingUsageObservation>, deadlineSlackSeconds: Long) {
        val now = utcNow()
        jdbc.batchUpdate("""
            insert into tbl_serving_usage(id, key_id, access_id, cluster_id, user_id, model, started_at, deadline_at, finished_at,
                duration_millis, http_status, wire_status, outcome, input_tokens, output_tokens, usage_missing, created_at, updated_at, ttfb_millis)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing
        """.trimIndent(), events, events.size) { ps, event ->
            val startedAt = LocalDateTime.ofInstant(event.startedAt, ZoneOffset.UTC)
            val duration = event.durationMillis.coerceAtLeast(0)
            ps.setObject(1, event.requestId); ps.setObject(2, event.identity.keyId); ps.setObject(3, event.identity.accessId)
            ps.setObject(4, event.identity.clusterId); ps.setObject(5, event.identity.userId); ps.setString(6, event.model)
            ps.setObject(7, startedAt); ps.setObject(8, startedAt.plusSeconds(deadlineSlackSeconds))
            ps.setObject(9, startedAt.plusNanos(duration * 1_000_000)); ps.setLong(10, duration); ps.setInt(11, event.httpStatus)
            ps.setObject(12, event.wireStatus, Types.INTEGER); ps.setString(13, normalize(event.outcome))
            ps.setObject(14, event.inputTokens?.takeIf { it >= 0 }, Types.BIGINT); ps.setObject(15, event.outputTokens?.takeIf { it >= 0 }, Types.BIGINT)
            ps.setBoolean(16, event.usageMissing); ps.setObject(17, now); ps.setObject(18, now)
            ps.setObject(19, event.ttfbMillis?.takeIf { it >= 0 }, Types.BIGINT)
        }
    }

    override fun insertRejections(events: List<ServingRejectionObservation>) {
        val now = utcNow()
        jdbc.batchUpdate("""
            insert into tbl_serving_rejection(id, key_id, access_id, cluster_id, user_id, model, reason, minute, count, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do nothing
        """.trimIndent(), events, events.size) { ps, event ->
            ps.setObject(1, event.id); ps.setObject(2, event.identity.keyId); ps.setObject(3, event.identity.accessId)
            ps.setObject(4, event.identity.clusterId); ps.setObject(5, event.identity.userId); ps.setString(6, event.model)
            ps.setString(7, event.reason); ps.setObject(8, LocalDateTime.ofInstant(event.minute, ZoneOffset.UTC)); ps.setInt(9, event.count)
            ps.setObject(10, now)
        }
    }

    private fun normalize(outcome: String) = outcome.uppercase().takeIf { it in OUTCOMES } ?: "FAILED"

    private companion object { val OUTCOMES = setOf("COMPLETED", "FAILED", "TIMEOUT", "CANCELLED", "INCOMPLETE") }
}
