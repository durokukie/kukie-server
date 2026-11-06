package com.duro.kukie.serving.infrastructure

import com.duro.kukie.serving.domain.ConnectorModelStatus
import com.duro.kukie.serving.domain.ConnectorModelStatuses
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Repository
class PostgresConnectorModelStatuses(private val jdbc: JdbcTemplate) : ConnectorModelStatuses {
    private val mapper = JsonMapper.builder().build()
    override fun find(clusterId: UUID): ConnectorModelStatus? = jdbc.query("select * from tbl_connector_model_status where cluster_id = ?", { rs, _ ->
        ConnectorModelStatus(rs.getObject("cluster_id", UUID::class.java), rs.getString("token_hash"), rs.getBoolean("connected"), rs.getTimestamp("checked_at")?.toLocalDateTime(), rs.getTimestamp("received_at").toLocalDateTime(), mapper.readValue(rs.getString("ready_models"), Array<String>::class.java).toList())
    }, clusterId).firstOrNull()
    override fun save(status: ConnectorModelStatus) {
        // Reject reports from a rotated/revoked registration, even during races.
        jdbc.update("""
            insert into tbl_connector_model_status(cluster_id, token_hash, connected, checked_at, received_at, ready_models)
            select cluster_id, token_hash, ?, ?, ?, ?::jsonb from tbl_connector_credential
            where cluster_id = ? and token_hash = ? and expires_at > ?
            on conflict(cluster_id) do update set token_hash = excluded.token_hash, connected = excluded.connected,
                checked_at = excluded.checked_at, received_at = excluded.received_at, ready_models = excluded.ready_models
        """.trimIndent(), status.connected, status.checkedAt, status.receivedAt, mapper.writeValueAsString(status.readyModels), status.clusterId, status.tokenHash, status.receivedAt)
    }
}
