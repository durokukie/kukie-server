package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AcceptServingInvitationService(private val permission: ServingAccessPermission, private val users: UserRepository) {
    @Transactional
    operator fun invoke(accessId: UUID, userId: UUID) {
        val access = permission.lockAccess(accessId)
        access.accept(userId, users.findByIdOrThrow(userId).email, utcNow())
    }
}
