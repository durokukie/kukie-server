package com.duro.kukie.user.application

import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import com.duro.kukie.user.presentation.dto.request.CreateUserRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateUserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val verificationCodeRepository: VerificationCodeRepository,
) {

    @Transactional
    fun createUser(request: CreateUserRequest) {
        if (userRepository.existsByEmail(request.email)) {
            throw DuplicatedEmailException()
        }

        val code = verificationCodeRepository.findByEmail(request.email)
        if (code == null || code != request.verificationCode) {
            throw InvalidVerificationCodeException()
        }
        verificationCodeRepository.deleteByEmail(request.email)

        val user = User(
            name = request.name,
            email = request.email,
            rawPassword = request.password,
            passwordEncoder = passwordEncoder,
        )
        userRepository.save(user)
    }
}
