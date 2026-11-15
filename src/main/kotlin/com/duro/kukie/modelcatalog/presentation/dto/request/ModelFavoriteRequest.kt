package com.duro.kukie.modelcatalog.presentation.dto.request

import com.duro.kukie.cluster.application.ServedModelName
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ModelFavoriteRequest(
    @field:Size(min = 3, max = 200, message = "모델 이름은 3자 이상 200자 이하여야 합니다.")
    @field:Pattern(
        regexp = ServedModelName.MODEL_PATTERN,
        message = "모델은 허깅페이스의 조직/이름 형식이어야 합니다.",
    )
    val model: String,
)
