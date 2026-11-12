package com.duro.kukie.modelcatalog.application.port.out

import com.duro.kukie.modelcatalog.domain.HuggingFaceModel

interface HuggingFaceClient {

    /** author 계정의 텍스트 생성 모델을 최근 30일 다운로드 순으로 limit 개. usedStorageBytes 는 비어 있다. 읽지 못하면 ModelCandidateRefreshFailedException 을 던진다. */
    fun fetchPopular(author: String, limit: Int): List<HuggingFaceModel>

    /** usedStorageBytes 까지 채운다. 읽지 못하면 ModelCandidateRefreshFailedException 을 던진다. */
    fun fetchModel(modelId: String): HuggingFaceModel
}
