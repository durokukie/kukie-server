package com.duro.kukie.team.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.AdminRequiredException
import com.duro.kukie.team.exception.NotTeamAdminException
import com.duro.kukie.team.exception.NotTeamMemberException
import com.duro.kukie.team.exception.TeamNotFoundException
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

interface TeamControllerDocs {

    @Operation(summary = "팀 생성", description = "팀을 만들고 생성자를 관리자로 등록합니다.")
    @ApiResponse(responseCode = "201", description = "Created")
    fun createTeam(userId: UUID, request: CreateTeamRequest): ResponseEntity<TeamResponse>

    @Operation(summary = "내 팀 목록 조회", description = "자신이 속한 팀과 각 팀에서의 역할을 조회합니다.")
    fun getMyTeams(userId: UUID): ResponseEntity<List<TeamResponse>>

    @Operation(summary = "팀 구성원 조회", description = "팀의 구성원 목록을 조회합니다. 해당 팀의 구성원만 조회할 수 있습니다.")
    @ApiErrorResponses(NotTeamMemberException::class)
    fun getTeamMembers(teamId: UUID, userId: UUID): ResponseEntity<List<TeamMemberResponse>>

    @Operation(summary = "팀 정보 수정", description = "팀 이름을 변경합니다. 관리자만 할 수 있습니다.")
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, TeamNotFoundException::class)
    fun updateTeam(teamId: UUID, userId: UUID, request: UpdateTeamRequest): ResponseEntity<TeamResponse>

    @Operation(
        summary = "팀 구성원 역할 변경",
        description = "구성원의 역할을 변경합니다. 관리자만 할 수 있으며, 마지막 관리자는 강등할 수 없습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, AdminRequiredException::class)
    fun updateTeamMemberRole(
        teamId: UUID,
        targetUserId: UUID,
        userId: UUID,
        request: UpdateTeamMemberRoleRequest,
    ): ResponseEntity<Unit>

    @Operation(
        summary = "팀 구성원 제거",
        description = "구성원을 팀에서 제거합니다. 관리자만 할 수 있으며, 마지막 관리자는 제거할 수 없습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, AdminRequiredException::class)
    fun removeTeamMember(teamId: UUID, targetUserId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(summary = "팀 삭제", description = "팀과 구성원 정보를 삭제합니다. 관리자만 할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class, TeamNotFoundException::class)
    fun deleteTeam(teamId: UUID, userId: UUID): ResponseEntity<Unit>
}
