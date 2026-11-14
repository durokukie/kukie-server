package com.duro.kukie.user.application

import com.duro.kukie.auth.exception.UnauthorizedException
import com.duro.kukie.user.domain.UserRepository
import com.duro.kukie.user.domain.findByIdOrThrow
import com.duro.kukie.user.presentation.dto.response.UserResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetUserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): UserResponse {
        // 액세스 토큰은 상태를 안 보는 JWT 라 탈퇴 뒤에도 만료 전까지 통한다. 에이전트가 사용자를 확인하는 이 길만은 막는다
        val user = userRepository.findByIdOrThrow(userId).takeUnless { it.isDeleted } ?: throw UnauthorizedException()

        return UserResponse.from(user)
    }
}
