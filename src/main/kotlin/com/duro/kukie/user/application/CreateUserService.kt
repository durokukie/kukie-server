package com.duro.kukie.user.application

import com.duro.kukie.user.application.port.`in`.CreateUserCommand
import com.duro.kukie.user.domain.User
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.VerificationCodeRepository
import com.duro.kukie.user.exception.DuplicatedEmailException
import com.duro.kukie.user.exception.InvalidVerificationCodeException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
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
    operator fun invoke(command: CreateUserCommand) {
        if (userRepository.existsByEmail(command.email)) {
            throw DuplicatedEmailException()
        }

        val code = verificationCodeRepository.findByEmail(command.email)
        if (code == null || code != command.verificationCode) {
            throw InvalidVerificationCodeException()
        }

        val user = User(
            name = command.name,
            email = command.email,
            rawPassword = command.password,
            passwordEncoder = passwordEncoder,
        )

        try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            val cause = e.cause
            if (cause is ConstraintViolationException && cause.constraintName == User.EMAIL_UNIQUE_CONSTRAINT) {
                throw DuplicatedEmailException()
            }
            throw e
        }

        verificationCodeRepository.deleteByEmail(command.email)
    }
}
