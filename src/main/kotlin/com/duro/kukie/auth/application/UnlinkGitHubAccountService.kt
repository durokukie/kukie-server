package com.duro.kukie.auth.application

import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UnlinkGitHubAccountService(
    private val userRepository: UserRepository,
) {

    @Transactional
    operator fun invoke(userId: UUID) {
        userRepository.findByIdOrThrow(userId).unlinkGitHub()
    }
}
