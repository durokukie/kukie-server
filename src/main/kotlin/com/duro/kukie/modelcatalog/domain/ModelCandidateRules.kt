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
    val downloads: Long = 0,
)

data class ModelSelection(
    val kept: List<CollectedModel>,
    /** 모델 id 와 걸러진 이유. */
    val dropped: Map<String, String>,
)

/** 회사별 허깅페이스 다운로드 순 목록에서 파라미터 수가 범위 안이고 라이선스가 걸리지 않고 vLLM 이 띄울 수 있는 원작자 모델만 남긴다. 목록 순서를 지킨다. */
object ModelCandidateRules {

    const val MIN_PARAMS_B = 20.0
    const val MAX_PARAMS_B = 130.0

    /**
     * GPU 1장에 올릴 수 있는 가중치 크기. 나머지 메모리는 KV 캐시 자리다.
     * L40S 46GB 는 2026-10-25 에 31GB 모델이 뜨고 KV 자리가 남은 실측에서 잡았고, RTX PRO 6000 96GB 는 같은 비율로 어림했다.
     */
    val GPU_MAX_WEIGHT_GB: Map<String, Double> = linkedMapOf("l40s" to 35.0, "pro6000" to 73.0)

    /** 허깅페이스 계정 이름 그대로. 갱신 때 이 계정마다 목록을 따로 받는다. */
    val AUTHOR_ACCOUNTS: List<String> = listOf(
        "Qwen", "deepseek-ai", "zai-org", "KRAFTON", "openai", "google", "mistralai", "meta-llama",
        "naver-hyperclovax", "LGAI-EXAONE", "kakaocorp", "skt", "upstage",
        "XiaomiMiMo", "MiniMaxAI", "moonshotai", "inclusionAI", "stepfun-ai", "baidu", "tencent",
        "nvidia", "microsoft", "ibm-granite", "allenai", "CohereLabs",
        "LiquidAI", "Nanbeige", "RecursiveSI", "reflection",
    )

    val AUTHORS: Set<String> = AUTHOR_ACCOUNTS.map(String::lowercase).toSet()

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

    /**
     * 한 회사가 목록을 채우지 않게 회사마다 다운로드가 많은 앞 perAuthor 개만 남긴다. pinned(검증·즐겨찾기 모델)는 수에 넣지 않고 늘 남긴다.
     * 순서는 회사별 1등끼리, 2등끼리… 로 묶고 같은 등수 안에서는 다운로드가 많은 쪽이 앞이다.
     */
    fun spreadAuthors(models: List<CollectedModel>, perAuthor: Int, pinned: Set<String>): List<CollectedModel> {
        val ranked = models.groupBy { it.id.substringBefore("/").lowercase() }.values.flatMap { sameAuthor ->
            val byDownloads = sameAuthor.sortedByDescending { it.downloads }
            val capped = byDownloads.filterNot { it.id in pinned }.take(perAuthor).map { it.id }.toSet()
            byDownloads.filter { it.id in pinned || it.id in capped }.mapIndexed { rank, model -> rank to model }
        }
        return ranked.sortedWith(compareBy<Pair<Int, CollectedModel>> { it.first }.thenByDescending { it.second.downloads })
            .map { it.second }
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

    /** fitsGpus 는 1장 기준이라 여러 장에서 잰 모델은 실측한 장수로 따로 본다. */
    fun fitsMeasured(model: CollectedModel, verified: VerifiedModel): Boolean {
        val weightGb = model.weightGb ?: return false
        val maxWeightGb = GPU_MAX_WEIGHT_GB[verified.gpu] ?: return false
        return weightGb <= maxWeightGb * verified.gpus
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
        if (exceedsColumns(model, license)) return "저장할 수 없는 긴 값"
        // 오븐은 vLLM 으로만 띄운다 — 공식 vLLM 에도 포크 표에도 없는 구조는 시작해도 뜨지 않는다
        val architecture = model.architectures.firstOrNull()
        if (vllmSupport(architecture) == VllmSupport.UNKNOWN) return "vLLM 지원 목록에 없는 구조 (${architecture ?: "모름"})"
        return null
    }

    private fun exceedsColumns(model: HuggingFaceModel, license: String?): Boolean =
        model.id.length > ModelCandidate.MAX_MODEL_ID_LENGTH ||
            (model.architectures.firstOrNull()?.length ?: 0) > ModelCandidate.MAX_ARCHITECTURE_LENGTH ||
            (license?.length ?: 0) > ModelCandidate.MAX_LICENSE_LENGTH

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
            downloads = model.downloads,
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
