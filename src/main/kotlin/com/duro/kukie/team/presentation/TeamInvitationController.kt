package com.duro.kukie.team.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.team.application.AcceptTeamInvitationService
import com.duro.kukie.team.application.CancelTeamInvitationService
import com.duro.kukie.team.application.DeclineTeamInvitationService
import com.duro.kukie.team.application.InviteTeamMemberService
import com.duro.kukie.team.domain.TeamRole
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/teams")
class TeamInvitationController(
    private val inviteTeamMemberService: InviteTeamMemberService,
    private val cancelTeamInvitationService: CancelTeamInvitationService,
    private val acceptTeamInvitationService: AcceptTeamInvitationService,
    private val declineTeamInvitationService: DeclineTeamInvitationService,
) : TeamInvitationControllerDocs {

    @TeamRoleRequired(TeamRole.ADMIN)
    @PostMapping("/{teamId}/invitations")
    override fun inviteTeamMember(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: InviteTeamMemberRequest,
    ): ResponseEntity<TeamInvitationResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteTeamMemberService(request.toCommand(teamId, userId)))
    }

    @TeamRoleRequired(TeamRole.ADMIN)
    @DeleteMapping("/{teamId}/invitations/{invitationId}")
    override fun cancelTeamInvitation(
        @PathVariable teamId: UUID,
        @PathVariable invitationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        cancelTeamInvitationService(teamId, invitationId, userId)

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/invitations/{invitationId}/accept")
    override fun acceptTeamInvitation(
        @PathVariable invitationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        acceptTeamInvitationService(invitationId, userId)

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/invitations/{invitationId}/decline")
    override fun declineTeamInvitation(
        @PathVariable invitationId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        declineTeamInvitationService(invitationId, userId)

        return ResponseEntity.noContent().build()
    }
}
