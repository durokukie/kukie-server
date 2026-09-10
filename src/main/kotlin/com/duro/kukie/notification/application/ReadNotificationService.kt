package com.duro.kukie.notification.application

import com.duro.kukie.notification.domain.NotificationRepository
import com.duro.kukie.notification.domain.findByIdOrThrow
import com.duro.kukie.notification.exception.NotMyNotificationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReadNotificationService(
    private val notificationRepository: NotificationRepository,
) {

    /** 읽음 표시. 앱이 "안 읽은 알림" 개수를 세려면 읽은 시각이 채워져야 한다. */
    @Transactional
    operator fun invoke(notificationId: UUID, userId: UUID) {
        val notification = notificationRepository.findByIdOrThrow(notificationId)
        if (notification.userId != userId) {
            throw NotMyNotificationException()
        }

        notification.markRead()
    }
}
