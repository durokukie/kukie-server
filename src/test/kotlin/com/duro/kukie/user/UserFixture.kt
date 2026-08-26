package com.duro.kukie.user

import com.duro.kukie.user.domain.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

object UserFixture {

    const val DEFAULT_NAME = "테스트"
    const val DEFAULT_EMAIL = "test@example.com"
    const val DEFAULT_PASSWORD = "password123"

    private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder()

    fun user(
        name: String = DEFAULT_NAME,
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD,
    ): User = User(
        name = name,
        email = email,
        rawPassword = password,
        passwordEncoder = passwordEncoder,
    )
}
