package com.duro.kukie.serving.infrastructure

import com.duro.kukie.global.util.utcNow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ServingUsageMaintenance(private val jdbc: JdbcTemplate) {
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    fun clean() {
        val now = utcNow()
        jdbc.update("update tbl_serving_usage set outcome = 'INTERRUPTED', usage_missing = (model is not null), updated_at = ? where outcome = 'IN_PROGRESS' and deadline_at <= ?", now, now)
        jdbc.update("delete from tbl_serving_usage where started_at < ?", now.toLocalDate().minusDays(89).atStartOfDay())
    }
}
