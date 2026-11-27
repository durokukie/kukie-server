package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.TeamNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID> {

    @Query(value = "select id from tbl_team where id = :id for update", nativeQuery = true)
    fun lockIdById(@Param("id") id: UUID): UUID?

    @Query("select t.id from Team t where t.reportEnabled = true and t.reportNextAt <= :now order by t.reportNextAt, t.id")
    fun findReportDueIds(@Param("now") now: LocalDateTime): List<UUID>

    /** 다른 서버가 잡고 있거나 이미 회차를 넘긴 팀이면 null. */
    @Query(
        value = "select * from tbl_team where id = :id and report_enabled and report_next_at <= :now for update skip locked",
        nativeQuery = true,
    )
    fun lockReportDue(@Param("id") id: UUID, @Param("now") now: LocalDateTime): Team?
}

fun TeamRepository.findByIdOrThrow(id: UUID): Team =
    findByIdOrNull(id) ?: throw TeamNotFoundException()
