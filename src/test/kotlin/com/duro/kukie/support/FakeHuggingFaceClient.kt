package com.duro.kukie.support

import com.duro.kukie.modelcatalog.application.port.out.HuggingFaceClient
import com.duro.kukie.modelcatalog.domain.HuggingFaceModel
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException

class FakeHuggingFaceClient : HuggingFaceClient, Resettable {

    var trending: List<HuggingFaceModel> = emptyList()

    /** 트렌딩 목록에 없어도 id 로 찾을 수 있는 모델. */
    val models = mutableMapOf<String, HuggingFaceModel>()

    /** 목록에는 있어도 id 로 읽으면 실패하는 모델. */
    val missing = mutableSetOf<String>()

    var failing = false

    /** 허깅페이스를 부른 횟수. 읽기가 허깅페이스를 부르지 않는지 본다. */
    var calls = 0
        private set

    override fun fetchTrending(limit: Int): List<HuggingFaceModel> {
        calls++
        if (failing) throw ModelCandidateRefreshFailedException()
        return trending
    }

    override fun fetchModel(modelId: String): HuggingFaceModel {
        calls++
        if (failing || modelId in missing) throw ModelCandidateRefreshFailedException()
        return models[modelId] ?: trending.firstOrNull { it.id == modelId } ?: throw ModelCandidateRefreshFailedException()
    }

    override fun clear() {
        trending = emptyList()
        models.clear()
        missing.clear()
        failing = false
        calls = 0
    }
}
