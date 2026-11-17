package com.duro.kukie.hosting.presentation.dto.request

import com.duro.kukie.hosting.application.port.`in`.JoinHostingCommand
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class JoinHostingRequest(
    @field:Schema(description = "서비스 종료일(closesOn)에 앱과 데이터가 지워진다는 안내에 동의했는지. true 여야 신청됩니다.")
    val agreed: Boolean,
) {
    fun toCommand(userId: UUID) = JoinHostingCommand(userId, agreed)
}
