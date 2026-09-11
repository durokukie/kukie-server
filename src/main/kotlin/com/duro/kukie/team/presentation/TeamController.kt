package com.duro.kukie.team.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.team.application.*
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@Authenticated
@RestController
@RequestMapping("/teams")
class TeamController(
    private val createTeamService: CreateTeamService,
    private val getMyTeamsService: GetMyTeamsService,
    private val getTeamMembersService: GetTeamMembersService,
    private val updateTeamService: UpdateTeamService,
    private val inviteTeamMemberService: InviteTeamMemberService,
    private val updateTeamMemberRoleService: UpdateTeamMemberRoleService,
    private val removeTeamMemberService: RemoveTeamMemberService,
    private val leaveTeamService: LeaveTeamService,
    private val deleteTeamService: DeleteTeamService,
) : TeamControllerDocs {

    @PostMapping
    override fun createTeam(
        @AuthUser userId: UUID,
        @RequestBody @Valid request: CreateTeamRequest,
    ): ResponseEntity<TeamResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(createTeamService(userId, request))
    }

    @GetMapping
    override fun getMyTeams(
        @AuthUser userId: UUID,
    ): ResponseEntity<List<TeamResponse>> {
        return ResponseEntity.ok(getMyTeamsService(userId))
    }

    @GetMapping("/{teamId}/members")
    override fun getTeamMembers(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<List<TeamMemberResponse>> {
        return ResponseEntity.ok(getTeamMembersService(teamId, userId))
    }

    @PatchMapping("/{teamId}")
    override fun updateTeam(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: UpdateTeamRequest,
    ): ResponseEntity<TeamResponse> {
        return ResponseEntity.ok(updateTeamService(teamId, userId, request))
    }

    @PostMapping("/{teamId}/invitations")
    override fun inviteTeamMember(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: InviteTeamMemberRequest,
    ): ResponseEntity<TeamInvitationResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteTeamMemberService(teamId, userId, request))
    }

    @PatchMapping("/{teamId}/members/{targetUserId}")
    override fun updateTeamMemberRole(
        @PathVariable teamId: UUID,
        @PathVariable targetUserId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: UpdateTeamMemberRoleRequest,
    ): ResponseEntity<Unit> {
        updateTeamMemberRoleService(teamId, targetUserId, userId, request)

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{teamId}/members/me")
    override fun leaveTeam(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        leaveTeamService(teamId, userId)

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{teamId}/members/{targetUserId}")
    override fun removeTeamMember(
        @PathVariable teamId: UUID,
        @PathVariable targetUserId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        removeTeamMemberService(teamId, targetUserId, userId)

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{teamId}")
    override fun deleteTeam(
        @PathVariable teamId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<Unit> {
        deleteTeamService(teamId, userId)

        return ResponseEntity.noContent().build()
    }
}
