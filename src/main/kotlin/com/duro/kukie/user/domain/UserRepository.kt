package com.duro.kukie.user.domain

import com.duro.kukie.user.exception.UserNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): User?

    /**
     * 대소문자를 무시한 조회. 목록으로 받는 이유는 `tbl_user.email` 유니크 제약이 대소문자를 구분해서
     * `Kim@x.com` 과 `kim@x.com` 이 서로 다른 계정으로 함께 있을 수 있기 때문이다 — 단건으로 받으면
     * 그때 예외가 난다.
     */
    fun findAllByEmailIgnoreCase(email: String): List<User>
}

fun UserRepository.findByIdOrThrow(id: UUID): User =
    findByIdOrNull(id) ?: throw UserNotFoundException()