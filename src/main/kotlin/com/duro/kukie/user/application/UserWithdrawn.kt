package com.duro.kukie.user.application

import com.duro.kukie.user.domain.GitHubAccount
import java.util.UUID

/** GitHub 를 연결한 회원의 탈퇴 이벤트. 익명화하면 연결이 지워지므로 그 전의 계정을 담는다. */
data class UserWithdrawn(
    val userId: UUID,
    val gitHub: GitHubAccount,
)
