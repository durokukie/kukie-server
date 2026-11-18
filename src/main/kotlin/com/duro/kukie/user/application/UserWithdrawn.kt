package com.duro.kukie.user.application

import com.duro.kukie.user.domain.GitHubAccount
import java.util.UUID

/** 회원의 탈퇴 이벤트. 익명화하면 GitHub 연결이 지워지므로 그 전의 계정을 담는다(연결하지 않았으면 null). */
data class UserWithdrawn(
    val userId: UUID,
    val gitHub: GitHubAccount?,
)
