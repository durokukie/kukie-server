package com.duro.kukie.modelcatalog.domain

/** 허깅페이스 목록 API 가 주는 값 가운데 거르기에 쓰는 칸. */
data class HuggingFaceModel(
    val id: String,
    val totalParams: Long?,
    val usedStorageBytes: Long?,
    val architectures: List<String>,
    val tags: List<String>,
    val gated: GatedAccess,
    val likes: Int,
    val createdAt: String?,
)
