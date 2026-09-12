package com.duro.kukie.team.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.*
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.*

/**
 * 없는 teamId 로 부르면 404 가 아니라 **403** 이다. 모든 팀 API 가 먼저 `TeamPermission` 을 거치는데,
 * 멤버십의 `team_id` FK 가 팀 존재를 보장하므로 없는 팀에는 멤버십도 없어 NOT_TEAM_MEMBER 가 먼저 난다.
 * 그래서 404 를 문서에 적지 않는다 — 클라이언트가 탈 수 없는 분기다 (자동 리뷰 지적).
 */
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
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class)
    fun updateTeam(teamId: UUID, userId: UUID, request: UpdateTeamRequest): ResponseEntity<TeamResponse>

    @Operation(
        summary = "팀 구성원 초대",
        description = "이메일 주소로 팀에 초대합니다. 관리자만 할 수 있으며, 받는 사람이 수락해야 구성원이 됩니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        AlreadyTeamMemberException::class,
        InvitationAlreadySentException::class,
    )
    fun inviteTeamMember(
        teamId: UUID,
        userId: UUID,
        request: InviteTeamMemberRequest,
    ): ResponseEntity<TeamInvitationResponse>

    @Operation(
        summary = "팀 초대 취소",
        description = "보낸 초대를 취소합니다. 관리자만 할 수 있으며, 아직 처리되지 않은 초대만 취소할 수 있습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        InvitationNotFoundException::class,
        InvitationNotPendingException::class,
    )
    fun cancelTeamInvitation(teamId: UUID, invitationId: UUID, userId: UUID): ResponseEntity<Unit>

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
        summary = "팀 나가기",
        description = "자신이 팀에서 나갑니다. 구성원이면 누구나 할 수 있으며, 마지막 관리자는 나갈 수 없습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, AdminRequiredException::class)
    fun leaveTeam(teamId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(
        summary = "팀 구성원 제거",
        description = "다른 구성원을 팀에서 제거합니다. 관리자만 할 수 있으며, 자기 자신은 제거할 수 없습니다(팀 나가기를 이용).",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        NotTeamMemberException::class,
        NotTeamAdminException::class,
        CannotRemoveSelfException::class,
        AdminRequiredException::class,
    )
    fun removeTeamMember(teamId: UUID, targetUserId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(summary = "팀 삭제", description = "팀과 구성원 정보를 삭제합니다. 관리자만 할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, NotTeamAdminException::class)
    fun deleteTeam(teamId: UUID, userId: UUID): ResponseEntity<Unit>
}
