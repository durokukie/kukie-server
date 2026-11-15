package com.duro.kukie.user.presentation.dto.response

import com.duro.kukie.user.domain.User
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val hasPassword: Boolean,
    val githubLogin: String?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val servingEnabled: Boolean? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val hostingEnabled: Boolean? = null,
) {
    companion object {
        fun from(user: User, servingEnabled: Boolean? = null, hostingEnabled: Boolean? = null) = UserResponse(
            id = user.id,
            name = user.name,
            email = user.email.value,
            hasPassword = user.hasPassword,
            githubLogin = user.githubLogin,
            servingEnabled = servingEnabled,
            hostingEnabled = hostingEnabled,
        )
    }
}
