package com.duro.kukie.auth.application

import com.duro.kukie.auth.domain.RefreshTokenRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class LogOutService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    fun logOut(userId: UUID) {
        refreshTokenRepository.delete(userId)
    }
}
