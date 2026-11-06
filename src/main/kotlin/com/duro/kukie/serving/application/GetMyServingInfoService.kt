package com.duro.kukie.serving.application

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetMyServingInfoService(private val permission: ServingAccessPermission, private val info: ServingInfo) {
    operator fun invoke(accessId: UUID, userId: UUID): ServingInfoResponse {
        val access = permission.requireActive(accessId, userId)
        return info.of(access.clusterId)
    }
}
