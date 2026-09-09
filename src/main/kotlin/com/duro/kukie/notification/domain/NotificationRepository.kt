package com.duro.kukie.notification.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<Notification>
}
