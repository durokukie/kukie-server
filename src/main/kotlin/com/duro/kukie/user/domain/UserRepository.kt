package com.duro.kukie.user.domain

import com.duro.kukie.global.domain.Email
import com.duro.kukie.user.exception.UserNotFoundException
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: Email): Boolean

    fun findByEmail(email: Email): User?

    fun existsByGithubId(githubId: Long): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun lockById(@Param("id") id: UUID): User?

    @Transactional
    @Modifying
    @Query("update User u set u.githubId = null, u.githubLogin = null where u.githubId = :githubId and u.id <> :userId")
    fun unlinkGitHubFromOthers(@Param("githubId") githubId: Long, @Param("userId") userId: UUID): Int
}

fun UserRepository.findByIdOrThrow(id: UUID): User =
    findByIdOrNull(id) ?: throw UserNotFoundException()

fun UserRepository.lockByIdOrThrow(id: UUID): User =
    lockById(id) ?: throw UserNotFoundException()
