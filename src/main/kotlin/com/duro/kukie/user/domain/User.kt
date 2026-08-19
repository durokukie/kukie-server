package com.duro.kukie.user.domain

import com.duro.kukie.global.entity.BaseTimeEntity
import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

@Entity
@Table(name = "tbl_user")
class User private constructor(
    name: String,
    email: String,
    password: String,
) : BaseTimeEntity() {
    constructor(name: String, email: String, rawPassword: String, passwordEncoder: PasswordEncoder) :this(
        name = name,
        email = email,
        password = checkNotNull(passwordEncoder.encode(rawPassword))
    )

    @Id
    var id: UUID = UuidCreator.getTimeOrderedEpoch()
        protected set

    @Column(nullable = false)
    var name = name
        protected set

    @Column(nullable = false, unique = true)
    var email = email
        protected set

    var password = password
        protected set
}