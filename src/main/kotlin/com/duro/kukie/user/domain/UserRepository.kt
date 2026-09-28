package com.duro.kukie.user.domain

import com.duro.kukie.user.exception.UserNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): User?

    /** 대소문자 무시 조회. `tbl_user.email` 유니크가 대소문자를 구분해 여러 건일 수 있어 목록으로 받는다. */
    // TODO: Emails.kt 삭제 후 모두 findByEmail로 대체
    fun findAllByEmailIgnoreCase(email: String): List<User>
}

fun UserRepository.findByIdOrThrow(id: UUID): User =
    findByIdOrNull(id) ?: throw UserNotFoundException()
