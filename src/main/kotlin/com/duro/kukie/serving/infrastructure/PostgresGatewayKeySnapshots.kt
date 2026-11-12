package com.duro.kukie.serving.infrastructure

import com.duro.kukie.serving.application.port.out.GatewayKeySnapshots
import com.duro.kukie.serving.application.port.out.UsableKeyRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class PostgresGatewayKeySnapshots(private val jdbc: JdbcTemplate) : GatewayKeySnapshots {
    private val row = RowMapper { rs, _ -> UsableKeyRow(
        rs.getObject("key_id", UUID::class.java), rs.getString("token_hash"),
        rs.getObject("cluster_id", UUID::class.java), rs.getTimestamp("expires_at").toLocalDateTime(),
    ) }

    override fun usable(now: LocalDateTime, limit: Int): List<UsableKeyRow> = jdbc.query("""
        select k.id as key_id, k.token_hash, k.expires_at, a.cluster_id
        from tbl_serving_api_key k join tbl_serving_access a on a.id = k.access_id
        where k.revoked_at is null and k.expires_at > ? and a.status = 'ACTIVE' and a.user_id is not null
        order by k.id limit ?
    """.trimIndent(), row, now, limit)
}
