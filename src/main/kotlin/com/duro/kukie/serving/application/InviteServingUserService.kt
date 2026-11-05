package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.`in`.InviteServingUserCommand
import com.duro.kukie.serving.domain.ServingAccess
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.domain.ServingAccessStatus
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.presentation.dto.response.ServingAccessResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InviteServingUserService(private val permission: ServingAccessPermission, private val accesses: ServingAccessRepository) {
    @Transactional
    operator fun invoke(command: InviteServingUserCommand): ServingAccessResponse {
        permission.lockCluster(command.clusterId)
        permission.requireAdmin(command.clusterId, command.userId)
        val now = utcNow()
        val existing = accesses.findByClusterIdAndEmailAndStatusIn(
            command.clusterId, command.email, listOf(ServingAccessStatus.PENDING, ServingAccessStatus.ACTIVE),
        )
        if (existing != null) {
            if (existing.statusAt(now) != ServingAccessStatus.EXPIRED) {
                throw ServingAccessException(ServingErrorCode.SERVING_ACCESS_ALREADY_EXISTS)
            }
            existing.expire(now)
            accesses.flush()
        }
        val access = accesses.save(ServingAccess(command.clusterId, command.email, command.userId, now.plusDays(7)))
        return ServingAccessResponse.of(access, now)
    }
}
