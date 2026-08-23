package com.duro.kukie.user.domain

import com.duro.kukie.user.exception.UserNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): User?
}

fun UserRepository.findByIdOrThrow(id: UUID): User =
    findByIdOrNull(id) ?: throw UserNotFoundException()