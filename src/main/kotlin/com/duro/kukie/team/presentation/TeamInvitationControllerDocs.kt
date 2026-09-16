package com.duro.kukie.team.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.team.exception.AlreadyTeamMemberException
import com.duro.kukie.team.exception.InsufficientTeamRoleException
import com.duro.kukie.team.exception.InvitationAlreadySentException
import com.duro.kukie.team.exception.InvitationExpiredException
import com.duro.kukie.team.exception.InvitationNotFoundException
import com.duro.kukie.team.exception.InvitationNotPendingException
import com.duro.kukie.team.exception.NotMyInvitationException
import com.duro.kukie.team.exception.NotTeamMemberException
import com.duro.kukie.team.presentation.dto.request.InviteTeamMemberRequest
import com.duro.kukie.team.presentation.dto.response.TeamInvitationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ResponseEntity
import java.util.UUID

/** teamId 를 받는 API 에 404 를 적지 않는 이유는 [TeamControllerDocs] 참고. */
interface TeamInvitationControllerDocs {

    @Operation(
        summary = "팀 구성원 초대",
        description = "이메일 주소로 팀에 초대합니다. 관리자만 할 수 있으며, 받는 사람이 수락해야 구성원이 됩니다. 초대는 7일 뒤 만료됩니다.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiErrorResponses(
        NotTeamMemberException::class,
        InsufficientTeamRoleException::class,
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
        InsufficientTeamRoleException::class,
        InvitationNotFoundException::class,
        InvitationNotPendingException::class,
    )
    fun cancelTeamInvitation(teamId: UUID, invitationId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(
        summary = "팀 초대 수락",
        description = "초대를 수락해 팀 구성원이 됩니다. 자신에게 온 초대만 수락할 수 있습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        InvitationNotFoundException::class,
        NotMyInvitationException::class,
        InvitationNotPendingException::class,
        InvitationExpiredException::class,
        AlreadyTeamMemberException::class,
    )
    fun acceptTeamInvitation(invitationId: UUID, userId: UUID): ResponseEntity<Unit>

    @Operation(
        summary = "팀 초대 거절",
        description = "초대를 거절합니다. 자신에게 온 초대만 거절할 수 있습니다.",
    )
    @ApiResponse(responseCode = "204", description = "No Content")
    @ApiErrorResponses(
        InvitationNotFoundException::class,
        NotMyInvitationException::class,
        InvitationNotPendingException::class,
        InvitationExpiredException::class,
    )
    fun declineTeamInvitation(invitationId: UUID, userId: UUID): ResponseEntity<Unit>
}
