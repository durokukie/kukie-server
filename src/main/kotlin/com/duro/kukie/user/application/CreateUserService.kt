package com.duro.kukie.user.application

import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun createUser(request: CreateUserRequest) {
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicatedEmailException()
        }

        val user = User(
            name = request.name,
            email = request.email,
            rawPassword = request.password,
            passwordEncoder = passwordEncoder,
        )
        userRepository.save(user)
    }
}
