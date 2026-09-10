package com.duro.kukie.notification.domain

import com.duro.kukie.notification.exception.NotificationNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<Notification>
}

fun NotificationRepository.findByIdOrThrow(id: UUID): Notification =
    findByIdOrNull(id) ?: throw NotificationNotFoundException()
