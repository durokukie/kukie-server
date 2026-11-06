package com.duro.kukie.serving.application

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetAdminServingInfoService(private val permission: ServingAccessPermission, private val info: ServingInfo) {
    operator fun invoke(clusterId: UUID, userId: UUID): ServingInfoResponse {
        permission.requireAdmin(clusterId, userId)
        return info.of(clusterId)
    }
}
