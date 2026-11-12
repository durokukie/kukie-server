package com.duro.kukie.modelcatalog.domain

/** 허깅페이스 목록 API 가 주는 값 가운데 거르기에 쓰는 칸. */
data class HuggingFaceModel(
    val id: String,
    val totalParams: Long?,
    val usedStorageBytes: Long?,
    /** 레포 루트의 safetensors 파일 크기 합. 파일 정보를 받지 않았거나 파일이 없으면 null. */
    val weightBytes: Long?,
    val architectures: List<String>,
    val tags: List<String>,
    val gated: GatedAccess,
    val likes: Int,
    val createdAt: String?,
    /** 최근 30일 다운로드 수. 회사가 다른 후보끼리 순서를 정할 때만 쓴다. */
    val downloads: Long = 0,
)
