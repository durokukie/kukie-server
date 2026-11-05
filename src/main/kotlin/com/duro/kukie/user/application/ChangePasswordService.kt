package com.duro.kukie.user.application

import com.duro.kukie.user.application.port.`in`.ChangePasswordCommand
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.exception.InvalidCurrentPasswordException
import com.duro.kukie.user.exception.PasswordNotSetException
import com.duro.kukie.user.exception.SamePasswordException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangePasswordService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    operator fun invoke(command: ChangePasswordCommand) {
        val user = userRepository.findByIdOrThrow(command.userId)
        if (!user.hasPassword) {
            throw PasswordNotSetException()
        }
        if (!user.matchesPassword(command.currentPassword, passwordEncoder)) {
            throw InvalidCurrentPasswordException()
        }
        if (command.currentPassword == command.newPassword) {
            throw SamePasswordException()
        }

        user.changePassword(command.newPassword, passwordEncoder)
    }
}
