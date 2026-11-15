package com.duro.kukie.modelcatalog.domain

data class VerifiedModel(
    val gpu: String,
    val maxConcurrency: Int,
    val measuredOn: String,
    val gpus: Int = 1,
)

/** 실측한 모델. 여기 있는 모델만 검증됨이다. maxConcurrency 는 TTFT p95 1초, ITL p95 50ms 안에서 잰 값이다. */
object VerifiedModels {

    val MODELS: Map<String, VerifiedModel> = mapOf(
        "openai/gpt-oss-20b" to VerifiedModel(gpu = "l40s", maxConcurrency = 32, measuredOn = "2026-10-25"),
        "openai/gpt-oss-120b" to VerifiedModel(gpu = "l40s", maxConcurrency = 8, measuredOn = "2026-11-04", gpus = 4),
    )
}
