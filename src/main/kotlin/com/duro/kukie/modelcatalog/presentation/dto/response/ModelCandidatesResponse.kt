package com.duro.kukie.modelcatalog.presentation.dto.response

import com.duro.kukie.modelcatalog.domain.GatedAccess
import com.duro.kukie.modelcatalog.domain.ModelCandidate
import com.duro.kukie.modelcatalog.domain.VerifiedModels
import com.duro.kukie.modelcatalog.domain.VllmSupport
import java.time.Instant
import java.time.ZoneOffset

data class ModelCandidatesResponse(
    /** 아직 한 번도 갱신하지 않았으면 null. */
    val generatedAt: Instant?,
    val candidates: List<CandidateResponse>,
) {
    data class CandidateResponse(
        val id: String,
        val paramsB: Double,
        val sizeGb: Double?,
        val weightGb: Double?,
        val license: String,
        val licenseNote: String?,
        val gated: Boolean,
        val vllmNative: Boolean,
        val gpus: List<String>,
        val verified: Boolean,
        val maxConcurrency: Int?,
        val measuredGpu: String?,
        val measuredGpus: Int?,
    ) {
        companion object {
            fun from(candidate: ModelCandidate): CandidateResponse {
                val verified = VerifiedModels.MODELS[candidate.modelId]
                return CandidateResponse(
                    id = candidate.modelId,
                    paramsB = candidate.paramsB,
                    sizeGb = candidate.sizeGb,
                    weightGb = candidate.weightGb,
                    license = candidate.license,
                    licenseNote = candidate.licenseNote,
                    gated = candidate.gated != GatedAccess.NONE,
                    vllmNative = candidate.vllmSupport == VllmSupport.UPSTREAM,
                    gpus = candidate.fitsGpus,
                    verified = verified != null,
                    maxConcurrency = verified?.maxConcurrency,
                    measuredGpu = verified?.gpu,
                    measuredGpus = verified?.gpus,
                )
            }
        }
    }

    companion object {
        fun from(candidates: List<ModelCandidate>) = ModelCandidatesResponse(
            generatedAt = candidates.maxOfOrNull { it.collectedAt }?.toInstant(ZoneOffset.UTC),
            candidates = candidates.sortedBy { it.displayOrder }.map(CandidateResponse::from).sortedBy { !it.verified },
        )
    }
}
