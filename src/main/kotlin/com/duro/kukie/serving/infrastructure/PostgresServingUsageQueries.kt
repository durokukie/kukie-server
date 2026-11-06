package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.out.ServingUsageQueries
import com.duro.kukie.serving.presentation.dto.response.ServingUsageGroup
import com.duro.kukie.serving.presentation.dto.response.ServingUsageResponse
import com.duro.kukie.serving.presentation.dto.response.ServingUsageTotals
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class PostgresServingUsageQueries(private val jdbc: NamedParameterJdbcTemplate) : ServingUsageQueries {
    override fun query(clusterId: UUID, userId: UUID?, from: LocalDate, to: LocalDate, admin: Boolean): ServingUsageResponse {
        val now = utcNow()
        val params = MapSqlParameterSource().addValue("cluster", clusterId).addValue("from", from.atStartOfDay()).addValue("until", to.plusDays(1).atStartOfDay()).addValue("now", now)
        val userFilter = if (userId != null) " and user_id = :user" else ""
        if (userId != null) params.addValue("user", userId)
        val source = """from (select *, case when outcome = 'IN_PROGRESS' and deadline_at <= :now then 'INTERRUPTED' else outcome end as effective_outcome
            from tbl_serving_usage where cluster_id = :cluster and started_at >= :from and started_at < :until $userFilter) u"""
        val aggregate = """count(*) as requests,
            count(*) filter (where effective_outcome = 'COMPLETED') as completed,
            count(*) filter (where effective_outcome not in ('COMPLETED','IN_PROGRESS')) as failed,
            count(*) filter (where effective_outcome = 'IN_PROGRESS') as in_progress,
            coalesce(sum(input_tokens),0) as input_tokens, coalesce(sum(output_tokens),0) as output_tokens,
            count(*) filter (where model is not null and (usage_missing or effective_outcome <> 'COMPLETED')) as missing_usage"""
        fun grouped(column: String): List<ServingUsageGroup> = jdbc.query("select $column as group_id, $aggregate $source group by $column order by $column", params) { rs, _ -> ServingUsageGroup(rs.getString("group_id"), totals(rs)) }
        val total = jdbc.query("select $aggregate, max(coalesce(finished_at, started_at)) as last_recorded $source", params) { rs, _ -> totals(rs) to rs.getTimestamp("last_recorded")?.toLocalDateTime() }.single()
        return ServingUsageResponse(from, to, now, total.second, 90, total.first, grouped("cast(started_at as date)"), grouped("key_id"), grouped("coalesce(model, '[models]')"), if (admin) grouped("user_id") else emptyList())
    }
    private fun totals(rs: ResultSet) = ServingUsageTotals(rs.getLong("requests"), rs.getLong("completed"), rs.getLong("failed"), rs.getLong("in_progress"), rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("missing_usage"))
}
