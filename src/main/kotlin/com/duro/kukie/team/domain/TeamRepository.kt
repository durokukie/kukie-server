package com.duro.kukie.team.domain

import com.duro.kukie.team.exception.TeamNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface TeamRepository : JpaRepository<Team, UUID>

fun TeamRepository.findByIdOrThrow(id: UUID): Team =
    findByIdOrNull(id) ?: throw TeamNotFoundException()
