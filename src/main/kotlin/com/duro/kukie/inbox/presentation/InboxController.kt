package com.duro.kukie.inbox.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.inbox.application.GetInboxService
import com.duro.kukie.inbox.presentation.dto.response.InboxResponse
import com.duro.kukie.team.application.AcceptTeamInvitationService
import com.duro.kukie.team.application.DeclineTeamInvitationService
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
    private val acceptTeamInvitationService: AcceptTeamInvitationService,
    private val declineTeamInvitationService: DeclineTeamInvitationService,
) : InboxControllerDocs {

    @GetMapping
    override fun getInbox(
        @AuthUser userId: UUID,
    ): ResponseEntity<InboxResponse> {
        return ResponseEntity.ok(getInboxService(userId))
    }

    @PostMapping("/invitations/{invitationId}/accept")
    override fun acceptInvitation(
        @PathVariable invitationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        acceptTeamInvitationService(invitationId, userId)

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/invitations/{invitationId}/decline")
    override fun declineInvitation(
        @PathVariable invitationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        declineTeamInvitationService(invitationId, userId)

        return ResponseEntity.noContent().build()
    }
}
