package com.duro.kukie.modelcatalog.exception

import com.duro.kukie.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ModelCatalogErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    MODEL_CANDIDATE_REFRESH_FAILED(
        HttpStatus.BAD_GATEWAY,
        "허깅페이스에서 모델 후보를 가져오지 못했습니다. 저장된 목록은 그대로입니다. 잠시 뒤 다시 시도해 주세요.",
    ),
    MODEL_CANDIDATE_REFRESH_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "방금 갱신했습니다. 잠시 뒤 다시 시도해 주세요."),
    ;

    override val code: String
        get() = name
}
