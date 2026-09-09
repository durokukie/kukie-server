package com.duro.kukie.team.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.team.application.CreateTeamService
import com.duro.kukie.team.application.DeleteTeamService
import com.duro.kukie.team.application.GetMyTeamsService
import com.duro.kukie.team.application.GetTeamMembersService
import com.duro.kukie.team.application.RemoveTeamMemberService
import com.duro.kukie.team.application.UpdateTeamMemberRoleService
import com.duro.kukie.team.application.UpdateTeamService
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/teams")
class TeamController(
    private val createTeamService: CreateTeamService,
    private val getMyTeamsService: GetMyTeamsService,
    private val getTeamMembersService: GetTeamMembersService,
    private val updateTeamService: UpdateTeamService,
    private val updateTeamMemberRoleService: UpdateTeamMemberRoleService,
    private val removeTeamMemberService: RemoveTeamMemberService,
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
