package com.duro.kukie.user.domain

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.exception.UserNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: Email): Boolean

    fun findByEmail(email: Email): User?
}

fun UserRepository.findByIdOrThrow(id: UUID): User =
    findByIdOrNull(id) ?: throw UserNotFoundException()
