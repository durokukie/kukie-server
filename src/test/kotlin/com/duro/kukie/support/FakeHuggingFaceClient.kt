package com.duro.kukie.support

import com.duro.kukie.modelcatalog.application.port.out.HuggingFaceClient
import com.duro.kukie.modelcatalog.domain.HuggingFaceModel
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException

class FakeHuggingFaceClient : HuggingFaceClient, Resettable {

    var popular: List<HuggingFaceModel> = emptyList()

    /** 다운로드 순 목록에 없어도 id 로 찾을 수 있는 모델. */
    val models = mutableMapOf<String, HuggingFaceModel>()

    /** 목록에는 있어도 id 로 읽으면 실패하는 모델. */
    val missing = mutableSetOf<String>()

    var failing = false

    /** 허깅페이스를 부르는 순간에 할 일. 호출 중인 상태를 볼 때 쓴다. */
    var onCall: () -> Unit = {}

    /** 허깅페이스를 부른 횟수. 읽기가 허깅페이스를 부르지 않는지 본다. */
    var calls = 0
        private set

    /** 단건으로 읽은 모델 id, 부른 순서. */
    val fetched = mutableListOf<String>()

    /** 마지막으로 목록을 몇 개 달라고 했는지. */
    var lastLimit: Int? = null
        private set

    override fun fetchPopular(limit: Int): List<HuggingFaceModel> {
        calls++
        lastLimit = limit
        onCall()
        if (failing) throw ModelCandidateRefreshFailedException()
        return popular
    }

    override fun fetchModel(modelId: String): HuggingFaceModel {
        calls++
        fetched += modelId
        if (failing || modelId in missing) throw ModelCandidateRefreshFailedException()
        return models[modelId] ?: popular.firstOrNull { it.id == modelId } ?: throw ModelCandidateRefreshFailedException()
    }

    override fun clear() {
        popular = emptyList()
        models.clear()
        missing.clear()
        failing = false
        onCall = {}
        calls = 0
        lastLimit = null
        fetched.clear()
    }
}
