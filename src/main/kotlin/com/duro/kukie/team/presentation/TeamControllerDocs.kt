package com.duro.kukie.team.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.AdminRequiredException
import com.duro.kukie.team.exception.CannotRemoveSelfException
import com.duro.kukie.team.exception.InsufficientTeamRoleException
import com.duro.kukie.team.exception.InvalidReportZoneException
import com.duro.kukie.team.exception.NotTeamMemberException
import com.duro.kukie.team.presentation.dto.request.CreateTeamRequest
import com.duro.kukie.team.presentation.dto.request.UpdateReportScheduleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamMemberRoleRequest
import com.duro.kukie.team.presentation.dto.request.UpdateTeamRequest
import com.duro.kukie.team.presentation.dto.response.TeamMemberResponse
import com.duro.kukie.team.presentation.dto.response.TeamResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

/** 없는 teamId 는 `TeamRoleInterceptor` 에서 403(NOT_TEAM_MEMBER)이 먼저 나므로 404 를 문서에 적지 않는다. */
interface TeamControllerDocs {

    @Operation(summary = "팀 생성", description = "팀을 만들고 생성자를 관리자로 등록합니다.")
    @ApiResponse(responseCode = "201", description = "Created")
    fun createTeam(userId: UUID, request: CreateTeamRequest): ResponseEntity<TeamResponse>

    @Operation(summary = "내 팀 목록 조회", description = "자신이 속한 팀과 각 팀에서의 역할, 주간 보고서 일정(reportSchedule)을 조회합니다.")
    fun getMyTeams(userId: UUID): ResponseEntity<List<TeamResponse>>

    @Operation(summary = "팀 구성원 조회", description = "팀의 구성원 목록을 조회합니다. 해당 팀의 구성원만 조회할 수 있습니다.")
    @ApiErrorResponses(NotTeamMemberException::class)
    fun getTeamMembers(teamId: UUID): ResponseEntity<List<TeamMemberResponse>>

    @Operation(summary = "팀 정보 수정", description = "팀 이름을 변경합니다. 관리자만 할 수 있습니다.")
    @ApiErrorResponses(NotTeamMemberException::class, InsufficientTeamRoleException::class)
    fun updateTeam(teamId: UUID, userId: UUID, request: UpdateTeamRequest): ResponseEntity<TeamResponse>

    @Operation(
        summary = "주간 보고서 일정 변경",
        description = "클러스터 주간 보고서를 자동으로 만들지(enabled)와 그 요일·시각을 바꿉니다. 관리자만 할 수 있습니다. 새 팀은 켜져 있습니다. " +
            "enabled 가 false 면 자동으로 만들지 않고(직접 만들기는 그대로 됩니다) 응답의 reportSchedule.nextAt 이 null 입니다. 다시 켜면 지난 회차를 " +
            "뒤늦게 만들지 않고 다음 회차부터 만듭니다. time 은 timeZone(IANA 시간대, 예: Asia/Seoul)의 " +
            "시각이고 초는 버립니다. 다음 보고서는 바꾼 일정에서 지금 뒤에 처음 오는 때(응답의 reportSchedule.nextAt, UTC)에 그 직전 일주일을 담아 만들어지므로, " +
            "바꾼 직후 한 번은 기간이 앞 보고서와 겹칠 수 있습니다. 모르는 시간대는 400 INVALID_REPORT_ZONE 입니다.",
    )
    @ApiErrorResponses(NotTeamMemberException::class, InsufficientTeamRoleException::class, InvalidReportZoneException::class)
    fun updateReportSchedule(teamId: UUID, userId: UUID, request: UpdateReportScheduleRequest): ResponseEntity<TeamResponse>

    @Operation(
        summary = "팀 구성원 역할 변경",
        description = "구성원의 역할을 변경합니다. 관리자만 할 수 있으며, 마지막 관리자는 강등할 수 없습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, InsufficientTeamRoleException::class, AdminRequiredException::class)
    fun updateTeamMemberRole(
        teamId: UUID,
        targetUserId: UUID,
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
        InsufficientTeamRoleException::class,
        CannotRemoveSelfException::class,
        AdminRequiredException::class,
    )
    fun removeTeamMember(teamId: UUID, targetUserId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(summary = "팀 삭제", description = "팀과 구성원 정보를 삭제합니다. 관리자만 할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(NotTeamMemberException::class, InsufficientTeamRoleException::class)
    fun deleteTeam(teamId: UUID): ResponseEntity<Unit>
}
