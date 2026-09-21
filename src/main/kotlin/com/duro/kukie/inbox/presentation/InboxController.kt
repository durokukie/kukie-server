package com.duro.kukie.inbox.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.inbox.application.GetInboxService
import com.duro.kukie.inbox.presentation.dto.response.InboxResponse
import com.duro.kukie.notification.application.ReadNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/inbox")
class InboxController(
    private val getInboxService: GetInboxService,
    private val readNotificationService: ReadNotificationService,
) : InboxControllerDocs {

    @GetMapping
    override fun getInbox(
        @AuthUser userId: UUID,
    ): ResponseEntity<InboxResponse> {
        return ResponseEntity.ok(getInboxService(userId))
    }

    @PostMapping("/notifications/{notificationId}/read")
    override fun readNotification(
        @PathVariable notificationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        readNotificationService(notificationId, userId)

        return ResponseEntity.noContent().build()
    }
}
