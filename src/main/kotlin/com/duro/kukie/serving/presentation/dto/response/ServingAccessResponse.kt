package com.duro.kukie.serving.presentation.dto.response

import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessStatus
import java.time.LocalDateTime
import java.util.UUID

data class ServingAccessResponse(
    val id: UUID,
    val email: String,
    val userId: UUID?,
    val status: ServingAccessStatus,
    val invitationExpiresAt: LocalDateTime,
) {
    companion object {
        fun of(access: ServingAccess, now: LocalDateTime) = ServingAccessResponse(
            access.id, access.email.value, access.userId, access.statusAt(now), access.invitationExpiresAt,
        )
    }
}

data class MyServingClusterResponse(
    val accessId: UUID, val clusterId: UUID, val clusterName: String, val providerName: String,
)

data class ServingInvitationResponse(
    val id: UUID, val clusterId: UUID, val clusterName: String,
    val providerName: String, val expiresAt: LocalDateTime,
)

data class ServingAccessPageResponse(
    val content: List<ServingAccessResponse>, val page: Int, val size: Int,
    val totalElements: Long, val totalPages: Int,
)
