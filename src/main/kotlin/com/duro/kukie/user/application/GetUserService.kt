package com.duro.kukie.user.application

import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.global.config.properties.HostingProperties
import com.duro.kukie.global.config.properties.InternalProperties
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetUserService(
    private val userRepository: UserRepository,
    private val internalProperties: InternalProperties,
    private val hostingProperties: HostingProperties,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): UserResponse {
        // 액세스 토큰은 상태를 안 보는 JWT 라 탈퇴 뒤에도 만료 전까지 통한다. 에이전트가 사용자를 확인하는 이 길만은 막는다
        val user = userRepository.findByIdOrThrow(userId).takeUnless { it.isDeleted } ?: throw UnauthorizedException()

        // 게이트웨이 토큰이 없으면 게이트웨이 API 가 모두 401 이라 서빙 화면이 동작하지 않는다
        return UserResponse.from(
            user,
            servingEnabled = internalProperties.gatewayToken.isNotEmpty(),
            hostingEnabled = hostingProperties.enabled,
        )
    }
}
