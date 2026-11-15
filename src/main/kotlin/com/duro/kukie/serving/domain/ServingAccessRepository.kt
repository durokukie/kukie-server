package com.duro.kukie.serving.domain

import com.duro.kukie.global.domain.Email
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface ServingAccessRepository : JpaRepository<ServingAccess, UUID> {
    fun findByClusterIdAndEmailAndStatusIn(clusterId: UUID, email: Email, status: Collection<ServingAccessStatus>): ServingAccess?

    fun findAllByClusterId(clusterId: UUID, pageable: Pageable): Page<ServingAccess>

    fun findAllByUserIdAndStatusOrderByCreatedAtDesc(userId: UUID, status: ServingAccessStatus): List<ServingAccess>

    fun findAllByEmailAndStatusAndInvitationExpiresAtAfterOrderByCreatedAtDesc(
        email: Email, status: ServingAccessStatus, now: LocalDateTime,
    ): List<ServingAccess>

    @Query("select a.clusterId from ServingAccess a where a.id = :id")
    fun findClusterIdById(@Param("id") id: UUID): UUID?
}
