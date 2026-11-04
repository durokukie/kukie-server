package com.duro.kukie.modelcatalog

import com.duro.kukie.modelcatalog.domain.GatedAccess
import com.duro.kukie.modelcatalog.domain.HuggingFaceModel

object ModelCatalogFixture {

    fun model(
        id: String,
        paramsB: Double? = 21.0,
        usedStorageGb: Double? = null,
        weightGb: Double? = null,
        license: String? = "apache-2.0",
        architecture: String? = "LlamaForCausalLM",
        gated: GatedAccess = GatedAccess.NONE,
    ) = HuggingFaceModel(
        id = id,
        totalParams = paramsB?.let { (it * 1e9).toLong() },
        usedStorageBytes = usedStorageGb?.let { (it * 1e9).toLong() },
        weightBytes = weightGb?.let { (it * 1e9).toLong() },
        architectures = listOfNotNull(architecture),
        tags = listOfNotNull(license?.let { "license:$it" }, "transformers"),
        gated = gated,
        likes = 10,
        createdAt = "2026-10-01T00:00:00.000Z",
    )

    fun gptOss20b() = model(
        id = "openai/gpt-oss-20b",
        paramsB = 20.914757184,
        usedStorageGb = 41.4,
        weightGb = 13.8,
        architecture = "GptOssForCausalLM",
    )

    fun gptOss120b() = model(
        id = "openai/gpt-oss-120b",
        paramsB = 116.829156672,
        usedStorageGb = 195.9,
        weightGb = 65.2,
        architecture = "GptOssForCausalLM",
    )
}
