package com.duro.kukie.serving.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ServingApiKeyRepository : JpaRepository<ServingApiKey, UUID> {
    fun findByTokenHash(tokenHash: String): ServingApiKey?
    fun findByIdAndAccessId(id: UUID, accessId: UUID): ServingApiKey?
    fun countByAccessIdAndRevokedAtIsNullAndExpiresAtAfter(accessId: UUID, now: LocalDateTime): Long
    fun findAllByAccessId(accessId: UUID, pageable: Pageable): Page<ServingApiKey>
}
