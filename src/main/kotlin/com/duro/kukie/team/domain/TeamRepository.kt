package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.TeamNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID> {

    @Query(value = "select id from tbl_team where id = :id for update", nativeQuery = true)
    fun lockIdById(@Param("id") id: UUID): UUID?
}

fun TeamRepository.findByIdOrThrow(id: UUID): Team =
    findByIdOrNull(id) ?: throw TeamNotFoundException()
