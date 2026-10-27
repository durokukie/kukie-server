package com.duro.kukie.user.application

import com.duro.kukie.user.application.port.`in`.UpdateUserCommand
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateUserService(
    private val userRepository: UserRepository,
) {

    @Transactional
    operator fun invoke(command: UpdateUserCommand): UserResponse {
        val user = userRepository.findByIdOrThrow(command.userId)
        user.update(command.name)

        return UserResponse.from(user)
    }
}
