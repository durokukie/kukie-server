package com.duro.kukie.serving.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetMyServingInfoService(private val permission: ServingAccessPermission, private val info: ServingInfo) {
    @Transactional(readOnly = true)
    operator fun invoke(accessId: UUID, userId: UUID): ServingInfoResponse {
        val access = permission.requireActive(accessId, userId)
        return info.of(access.clusterId)
    }
}
