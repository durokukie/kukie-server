package com.duro.kukie.user.presentation.dto.response

import com.duro.kukie.user.domain.User
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val name: String,
    val email: String,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            name = user.name,
            email = user.email,
        )
    }
}
