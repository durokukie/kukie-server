package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.AcceptServingInvitationService
import com.duro.kukie.serving.application.DeclineServingInvitationService
import com.duro.kukie.serving.application.GetMyServingClustersService
import com.duro.kukie.serving.application.GetMyServingInvitationsService
import com.duro.kukie.serving.application.GetServingAccessesService
import com.duro.kukie.serving.application.InviteServingUserService
import com.duro.kukie.serving.application.RevokeServingAccessService
import com.duro.kukie.serving.presentation.dto.request.InviteServingUserRequest
import com.duro.kukie.serving.presentation.dto.response.MyServingClusterResponse
import com.duro.kukie.serving.presentation.dto.response.ServingAccessResponse
import com.duro.kukie.serving.presentation.dto.response.ServingAccessPageResponse
import com.duro.kukie.serving.presentation.dto.response.ServingInvitationResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
class ServingAccessController(
    private val inviteUser: InviteServingUserService,
    private val acceptInvitation: AcceptServingInvitationService,
    private val declineInvitation: DeclineServingInvitationService,
    private val revokeAccess: RevokeServingAccessService,
    private val listAccesses: GetServingAccessesService,
    private val getMyClusters: GetMyServingClustersService,
    private val getMyInvitations: GetMyServingInvitationsService,
) {
    @PostMapping("/clusters/{clusterId}/serving/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@PathVariable clusterId: UUID, @AuthUser userId: UUID, @RequestBody @Valid request: InviteServingUserRequest): ServingAccessResponse =
        inviteUser(request.toCommand(clusterId, userId))

    @GetMapping("/clusters/{clusterId}/serving/accesses")
    fun list(
        @PathVariable clusterId: UUID, @AuthUser userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ServingAccessPageResponse = listAccesses(clusterId, userId, page, size)

    @DeleteMapping("/clusters/{clusterId}/serving/accesses/{accessId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable clusterId: UUID, @PathVariable accessId: UUID, @AuthUser userId: UUID) = revokeAccess(clusterId, accessId, userId)

    @GetMapping("/serving/clusters")
    fun myClusters(@AuthUser userId: UUID): List<MyServingClusterResponse> = getMyClusters(userId)

    @GetMapping("/serving/invitations")
    fun myInvitations(@AuthUser userId: UUID): List<ServingInvitationResponse> = getMyInvitations(userId)

    @PostMapping("/serving/invitations/{accessId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun accept(@PathVariable accessId: UUID, @AuthUser userId: UUID) = acceptInvitation(accessId, userId)

    @PostMapping("/serving/invitations/{accessId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decline(@PathVariable accessId: UUID, @AuthUser userId: UUID) = declineInvitation(accessId, userId)
}
