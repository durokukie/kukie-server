package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredential
import com.duro.kukie.serving.domain.ConnectorCredentials
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

@Repository
class PostgresConnectorCredentials(private val jdbc: JdbcTemplate) : ConnectorCredentials {
    private val mapper = JsonMapper.builder().build()
    private val row = RowMapper { rs, _ -> ConnectorCredential(
        rs.getObject("cluster_id", UUID::class.java), rs.getString("token_hash"),
        mapper.readValue(rs.getString("models"), Array<String>::class.java).toList(), rs.getTimestamp("expires_at").toLocalDateTime(),
        rs.getString("previous_token_hash"), rs.getTimestamp("previous_expires_at")?.toLocalDateTime(), rs.getTimestamp("rotated_at")?.toLocalDateTime(),
    ) }

    override fun lockCapacity() { jdbc.execute("select pg_advisory_xact_lock(hashtext('kukie.connector.registration')::bigint)") }
    override fun find(clusterId: UUID): ConnectorCredential? = jdbc.query("select * from tbl_connector_credential where cluster_id = ?", row, clusterId).firstOrNull()
    override fun findForUpdate(clusterId: UUID): ConnectorCredential? = jdbc.query("select * from tbl_connector_credential where cluster_id = ? for update", row, clusterId).firstOrNull()
    override fun active(now: LocalDateTime): List<ConnectorCredential> = jdbc.query("select * from tbl_connector_credential where expires_at > ? order by cluster_id limit 257", row, now)
    override fun save(credential: ConnectorCredential) {
        val now = utcNow()
        jdbc.update("""
            insert into tbl_connector_credential(cluster_id, token_hash, models, expires_at, previous_token_hash, previous_expires_at, rotated_at, created_at, updated_at)
            values (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
            on conflict(cluster_id) do update set token_hash = excluded.token_hash, models = excluded.models, expires_at = excluded.expires_at,
                previous_token_hash = excluded.previous_token_hash, previous_expires_at = excluded.previous_expires_at,
                rotated_at = excluded.rotated_at, updated_at = excluded.updated_at
        """.trimIndent(), credential.clusterId, credential.tokenHash, mapper.writeValueAsString(credential.models), credential.expiresAt,
            credential.previousTokenHash, credential.previousExpiresAt, credential.rotatedAt, now, now)
    }
    override fun revoke(clusterId: UUID, now: LocalDateTime) {
        jdbc.update("update tbl_connector_credential set expires_at = ?, previous_token_hash = null, previous_expires_at = null, rotated_at = null, updated_at = ? where cluster_id = ?", now, now, clusterId)
    }
}
