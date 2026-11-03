package com.duro.kukie.modelcatalog.infrastructure

import com.duro.kukie.global.config.properties.HuggingFaceProperties
import com.duro.kukie.global.util.logger
import com.duro.kukie.modelcatalog.application.port.out.HuggingFaceClient
import com.duro.kukie.modelcatalog.domain.GatedAccess
import com.duro.kukie.modelcatalog.domain.HuggingFaceModel
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.Duration

@Component
class RestHuggingFaceClient internal constructor(
    private val restClient: RestClient,
    private val properties: HuggingFaceProperties,
) : HuggingFaceClient {

    @Autowired
    constructor(
        restClientBuilder: RestClient.Builder,
        properties: HuggingFaceProperties,
    ) : this(restClientBuilder.requestFactory(timedRequestFactory()).baseUrl(properties.apiUrl).build(), properties)

    private val log = logger()

    override fun fetchTrending(limit: Int): List<HuggingFaceModel> {
        val models = call {
            restClient.get()
                .uri {
                    it.path("/models")
                        .queryParam("pipeline_tag", "text-generation")
                        .queryParam("sort", "trendingScore")
                        .queryParam("limit", limit)
                        .queryParam("expand[]", *LIST_EXPAND)
                        .build()
                }
                .headers(::authorize)
                .retrieve()
                .body<List<ModelEntry>>()
        }
        return models.map(ModelEntry::toModel)
    }

    override fun fetchModel(modelId: String): HuggingFaceModel {
        val model = call {
            restClient.get()
                .uri {
                    it.path("/models/{author}/{name}")
                        .queryParam("blobs", true)
                        .queryParam("expand[]", *MODEL_EXPAND)
                        .build(modelId.substringBefore("/"), modelId.substringAfter("/", ""))
                }
                .headers(::authorize)
                .retrieve()
                .body<ModelEntry>()
        }
        return model.toModel()
    }

    private fun authorize(headers: HttpHeaders) {
        properties.token?.takeUnless { it.isBlank() }?.let(headers::setBearerAuth)
    }

    private fun <T : Any> call(request: () -> T?): T {
        val body = try {
            request()
        } catch (exception: RestClientException) {
            log.warn("허깅페이스를 부르지 못했습니다: {}", exception.message)
            throw ModelCandidateRefreshFailedException()
        }
        if (body == null) {
            log.warn("허깅페이스 응답이 비어 있습니다")
            throw ModelCandidateRefreshFailedException()
        }
        return body
    }

    private class ModelEntry(
        val id: String,
        val likes: Int?,
        val gated: Any?,
        val createdAt: String?,
        val tags: List<String>?,
        val usedStorage: Long?,
        val safetensors: SafetensorsEntry?,
        val config: ConfigEntry?,
        val siblings: List<SiblingEntry>?,
    ) {
        fun toModel() = HuggingFaceModel(
            id = id,
            totalParams = safetensors?.total,
            usedStorageBytes = usedStorage,
            weightBytes = weightBytes(),
            architectures = config?.architectures.orEmpty(),
            tags = tags.orEmpty(),
            gated = when (gated) {
                null, false, "false" -> GatedAccess.NONE
                "manual" -> GatedAccess.MANUAL
                else -> GatedAccess.AUTO
            },
            likes = likes ?: 0,
            createdAt = createdAt,
        )

        // Mistral 레포는 같은 가중치를 consolidated 와 model 두 벌로 올린다. 둘 다 더하면 두 배가 된다
        private fun weightBytes(): Long? {
            val root = siblings.orEmpty().filter { it.rfilename.endsWith(".safetensors") && "/" !in it.rfilename }
            val weights = root.filterNot { it.rfilename.startsWith("consolidated") }.ifEmpty { root }
            return weights.mapNotNull { it.size }.takeIf { it.isNotEmpty() }?.sum()
        }
    }

    private class SafetensorsEntry(
        val total: Long?,
    )

    private class SiblingEntry(        val rfilename: String,
        val size: Long?,
    )

    private class ConfigEntry(
        val architectures: List<String>?,
    )

    companion object {
        private val LIST_EXPAND = arrayOf<Any>("safetensors", "config", "gated", "createdAt", "likes", "tags")

        // usedStorage 는 hf download 가 받는 레포 전체 크기고 목록 API 는 이 값을 400 으로 거부한다. 파일 크기는 blobs 로 받는다
        private val MODEL_EXPAND = arrayOf<Any>(*LIST_EXPAND, "usedStorage", "siblings")
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(15)

        private fun timedRequestFactory() =
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                .apply { setReadTimeout(READ_TIMEOUT) }
    }
}
