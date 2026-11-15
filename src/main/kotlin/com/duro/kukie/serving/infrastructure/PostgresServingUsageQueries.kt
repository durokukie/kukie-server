package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.out.ServingUsageQueries
import com.duro.kukie.serving.presentation.dto.response.*
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
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

    override fun overview(clusterId: UUID, model: String?): ServingUsageOverviewResponse {
        val now = utcNow()
        val from = now.truncatedTo(ChronoUnit.HOURS).minusHours(23)
        val params = MapSqlParameterSource().addValue("cluster", clusterId).addValue("from", from).addValue("last", from.plusHours(23))
            .addValue("until", from.plusHours(24)).addValue("now", now).addValue("model", model)
        val modelFilter = if (model != null) " and model = :model" else ""
        val usage = """(select *, case when outcome = 'IN_PROGRESS' and deadline_at <= :now then 'INTERRUPTED' else outcome end as effective_outcome
            from tbl_serving_usage where cluster_id = :cluster and started_at >= :from and started_at < :until $modelFilter)"""
        val rejections = "(select * from tbl_serving_rejection where cluster_id = :cluster and minute >= :from and minute < :until $modelFilter)"
        val failed = "effective_outcome not in ('COMPLETED','IN_PROGRESS')"
        val models = jdbc.queryForList("""select model from tbl_serving_usage where cluster_id = :cluster and started_at >= :from and started_at < :until and model is not null
            union select model from tbl_serving_rejection where cluster_id = :cluster and minute >= :from and minute < :until and model is not null order by 1""", params, String::class.java).filterNotNull()
        val byReason = jdbc.query("select reason, sum(count) as count from $rejections r group by reason order by count desc, reason", params) { rs, _ -> ServingRejectionCount(rs.getString("reason"), rs.getLong("count")) }
        val totals = jdbc.query("""select count(*) as requests,
            count(*) filter (where effective_outcome = 'COMPLETED') as completed, count(*) filter (where $failed) as failed,
            count(*) filter (where effective_outcome = 'IN_PROGRESS') as in_progress,
            percentile_cont(0.5) within group (order by duration_millis) filter (where finished_at is not null) as duration_p50,
            percentile_cont(0.95) within group (order by duration_millis) filter (where finished_at is not null) as duration_p95,
            percentile_cont(0.5) within group (order by ttfb_millis) as ttfb_p50, percentile_cont(0.95) within group (order by ttfb_millis) as ttfb_p95,
            coalesce(sum(input_tokens),0) as input_tokens, coalesce(sum(output_tokens),0) as output_tokens from $usage u""", params) { rs, _ ->
            ServingUsageOverviewTotals(rs.getLong("requests"), rs.getLong("completed"), rs.getLong("failed"), rs.getLong("in_progress"), byReason.sumOf { it.count },
                rs.rounded("duration_p50"), rs.rounded("duration_p95"), rs.rounded("ttfb_p50"), rs.rounded("ttfb_p95"), rs.getLong("input_tokens"), rs.getLong("output_tokens"))
        }.single()
        val hourly = jdbc.query("""select h.hour, coalesce(u.requests,0) as requests, coalesce(u.failed,0) as failed, coalesce(r.rejected,0) as rejected, u.ttfb_p95
            from generate_series(cast(:from as timestamp), cast(:last as timestamp), interval '1 hour') as h(hour)
            left join (select date_trunc('hour', started_at) as hour, count(*) as requests, count(*) filter (where $failed) as failed,
                percentile_cont(0.95) within group (order by ttfb_millis) as ttfb_p95 from $usage u group by 1) u on u.hour = h.hour
            left join (select date_trunc('hour', minute) as hour, sum(count) as rejected from $rejections r group by 1) r on r.hour = h.hour
            order by h.hour""", params) { rs, _ ->
            ServingUsageHour(rs.getTimestamp("hour").toLocalDateTime(), rs.getLong("requests"), rs.getLong("failed"), rs.getLong("rejected"), rs.rounded("ttfb_p95"))
        }
        val recent = jdbc.query("""select u.started_at, u.model, u.effective_outcome, u.http_status, u.duration_millis, u.ttfb_millis, u.input_tokens, u.output_tokens, usr.email
            from $usage u join tbl_user usr on usr.id = u.user_id order by u.started_at desc, u.id desc limit 20""", params) { rs, _ ->
            ServingUsageRecent(rs.getTimestamp("started_at").toLocalDateTime(), rs.getString("model"), rs.getString("effective_outcome"), (rs.getObject("http_status") as Number?)?.toInt(),
                rs.long("duration_millis"), rs.long("ttfb_millis"), rs.long("input_tokens"), rs.long("output_tokens"), rs.getString("email"))
        }
        return ServingUsageOverviewResponse(now, from, model, models, totals, byReason, hourly, recent)
    }

    private fun ResultSet.rounded(column: String): Long? = (getObject(column) as Number?)?.toDouble()?.roundToLong()
    private fun ResultSet.long(column: String): Long? = (getObject(column) as Number?)?.toLong()

    private fun totals(rs: ResultSet) = ServingUsageTotals(rs.getLong("requests"), rs.getLong("completed"), rs.getLong("failed"), rs.getLong("in_progress"), rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("missing_usage"))
}
