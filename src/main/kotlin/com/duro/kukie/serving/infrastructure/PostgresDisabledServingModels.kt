package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.DisabledServingModels
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PostgresDisabledServingModels(private val jdbc: JdbcTemplate) : DisabledServingModels {
    override fun find(clusterId: UUID): Set<String> = jdbc.queryForList(
        "select model from tbl_serving_disabled_model where cluster_id = ?", String::class.java, clusterId,
    ).filterNotNull().toSet()

    override fun setEnabled(clusterId: UUID, model: String, enabled: Boolean) {
        if (enabled) jdbc.update("delete from tbl_serving_disabled_model where cluster_id = ? and model = ?", clusterId, model)
        else jdbc.update("""
            insert into tbl_serving_disabled_model(cluster_id, model, updated_at) values (?, ?, ?)
            on conflict(cluster_id, model) do update set updated_at = excluded.updated_at
        """.trimIndent(), clusterId, model, utcNow())
    }
}
