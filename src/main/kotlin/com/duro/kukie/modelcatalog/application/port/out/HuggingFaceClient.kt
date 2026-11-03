package com.duro.kukie.modelcatalog.application.port.out

import com.duro.kukie.modelcatalog.domain.HuggingFaceModel

interface HuggingFaceClient {

    /** usedStorageBytes 는 비어 있다. 읽지 못하면 ModelCandidateRefreshFailedException 을 던진다. */
    fun fetchTrending(limit: Int): List<HuggingFaceModel>

    /** usedStorageBytes 까지 채운다. 읽지 못하면 ModelCandidateRefreshFailedException 을 던진다. */
    fun fetchModel(modelId: String): HuggingFaceModel
}
