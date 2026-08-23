package com.duro.kukie.user.application

import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetUserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getUser(userId: UUID): UserResponse {
        val user = userRepository.findByIdOrThrow(userId)

        return UserResponse.from(user)
    }
}
