package com.duro.kukie.modelcatalog.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class CollectedModel(
    val id: String,
    val paramsB: Double,
    val sizeGb: Double? = null,
    val weightGb: Double? = null,
    val architecture: String,
    val license: String,
    val licenseNote: String?,
    val vllmSupport: VllmSupport,
    val gated: GatedAccess,
    val fitsGpus: List<String> = emptyList(),
    val likes: Int,
    val registeredOn: LocalDate?,
)

data class ModelSelection(
    val kept: List<CollectedModel>,
    /** 모델 id 와 걸러진 이유. */
    val dropped: Map<String, String>,
)

/** 허깅페이스 트렌딩 목록에서 파라미터 수가 범위 안이고 라이선스가 걸리지 않는 원작자 모델만 남긴다. 목록 순서를 지킨다. */
object ModelCandidateRules {

    const val MIN_PARAMS_B = 20.0
    const val MAX_PARAMS_B = 130.0

    /**
     * GPU 1장에 올릴 수 있는 가중치 크기. 나머지 메모리는 KV 캐시 자리다.
     * L40S 46GB 는 2026-10-25 에 31GB 모델이 뜨고 KV 자리가 남은 실측에서 잡았고, RTX PRO 6000 96GB 는 같은 비율로 어림했다.
     */
    val GPU_MAX_WEIGHT_GB: Map<String, Double> = linkedMapOf("l40s" to 35.0, "pro6000" to 73.0)

    val AUTHORS: Set<String> = listOf(
        "Qwen", "deepseek-ai", "zai-org", "KRAFTON", "openai", "google", "mistralai", "meta-llama",
        "naver-hyperclovax", "LGAI-EXAONE", "kakaocorp", "skt", "upstage",
        "XiaomiMiMo", "MiniMaxAI", "moonshotai", "inclusionAI", "stepfun-ai", "baidu", "tencent",
        "nvidia", "microsoft", "ibm-granite", "allenai", "CohereLabs",
        "LiquidAI", "Nanbeige", "RecursiveSI", "reflection",
    ).map(String::lowercase).toSet()

    private val VARIANT_PATTERNS = listOf(
        "GGUF", "MLX", "AWQ", "GPTQ", "EXL2", "EXL3", "uncensored", "abliterated", "obliterated", "heretic",
    )

    private val OK_LICENSES = setOf("apache-2.0", "mit", "bsd", "bsd-2-clause", "bsd-3-clause", "isc", "unlicense", "cc0-1.0")
    private val NON_COMMERCIAL = listOf(Regex("(^|-)nc(-|$)"), Regex("exaone"), Regex("research"))
    private const val NOTE_CONDITIONAL = "조건 있음"
    private const val NOTE_UNCHECKED = "확인 필요"
    private const val PARAMS_PER_B = 1e9
    private const val BYTES_PER_GB = 1e9

    fun select(models: List<HuggingFaceModel>): ModelSelection {
        val kept = mutableListOf<CollectedModel>()
        val dropped = linkedMapOf<String, String>()
        models.distinctBy { it.id }.forEach { model ->
            val reason = rejection(model)
            if (reason == null) kept += collected(model) else dropped[model.id] = reason
        }
        return ModelSelection(kept, dropped)
    }

    /** 모델별로 받은 파일 크기로 내려받을 크기와 들어가는 GPU 를 채운다. 가중치 크기를 모르면 들어가는 GPU 가 없다고 본다. */
    fun withRepoFiles(model: CollectedModel, details: HuggingFaceModel): CollectedModel {
        val weightGb = details.weightBytes?.takeIf { it > 0 }?.let { it / BYTES_PER_GB }
        return model.copy(
            sizeGb = details.usedStorageBytes?.takeIf { it > 0 }?.let { oneDecimal(it / BYTES_PER_GB) },
            weightGb = weightGb?.let(::oneDecimal),
            fitsGpus = if (weightGb == null) emptyList() else GPU_MAX_WEIGHT_GB.filterValues { weightGb <= it }.keys.toList(),
        )
    }

    private fun rejection(model: HuggingFaceModel): String? {
        val author = model.id.substringBefore("/")
        val name = model.id.substringAfter("/", "")
        if (author.lowercase() !in AUTHORS || name.isEmpty()) return "원작자 아님 ($author)"
        VARIANT_PATTERNS.firstOrNull { name.contains(it, ignoreCase = true) }?.let { return "변형 이름 ($it)" }
        val total = model.totalParams?.takeIf { it > 0 } ?: return "파라미터 수 없음 (safetensors 아님)"
        val paramsB = total / PARAMS_PER_B
        if (paramsB !in MIN_PARAMS_B..MAX_PARAMS_B) return "크기 ${oneDecimal(paramsB)}B (범위 밖)"
        val license = licenseName(model)
        if (license != null && NON_COMMERCIAL.any { it.containsMatchIn(license) }) return "비상업 라이선스 ($license)"
        return null
    }

    private fun collected(model: HuggingFaceModel): CollectedModel {
        val paramsB = requireNotNull(model.totalParams) / PARAMS_PER_B
        val license = licenseName(model)
        return CollectedModel(
            id = model.id,
            paramsB = oneDecimal(paramsB),
            architecture = model.architectures.firstOrNull() ?: "?",
            license = license ?: "?",
            licenseNote = licenseNote(license),
            vllmSupport = vllmSupport(model.architectures.firstOrNull()),
            gated = model.gated,
            likes = model.likes,
            registeredOn = registeredOn(model.createdAt),
        )
    }

    private fun licenseName(model: HuggingFaceModel): String? =
        model.tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:")?.lowercase()

    private fun licenseNote(license: String?): String? = when {
        license == null || license == "other" -> NOTE_UNCHECKED
        license in OK_LICENSES -> null
        else -> NOTE_CONDITIONAL
    }

    private fun vllmSupport(architecture: String?): VllmSupport = when (architecture) {
        null -> VllmSupport.UNKNOWN
        in VllmArchitectures.UPSTREAM -> VllmSupport.UPSTREAM
        in VllmArchitectures.FORKS -> VllmSupport.FORK
        else -> VllmSupport.UNKNOWN
    }

    private fun registeredOn(createdAt: String?): LocalDate? = try {
        createdAt?.take(10)?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun oneDecimal(value: Double): Double = Math.round(value * 10) / 10.0
}
