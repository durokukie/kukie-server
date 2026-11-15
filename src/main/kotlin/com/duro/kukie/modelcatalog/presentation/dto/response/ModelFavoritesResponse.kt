package com.duro.kukie.modelcatalog.presentation.dto.response

/** 클러스터의 즐겨찾기 모델 id(허깅페이스 org/name). 넣은 순서다. */
data class ModelFavoritesResponse(
    val models: List<String>,
)
