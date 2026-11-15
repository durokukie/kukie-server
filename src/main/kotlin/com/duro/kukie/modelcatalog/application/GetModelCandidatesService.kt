package com.duro.kukie.modelcatalog.application

import com.duro.kukie.modelcatalog.domain.ModelCandidateRepository
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelCandidatesResponse
import org.springframework.stereotype.Service

@Service
class GetModelCandidatesService(
    private val modelCandidateRepository: ModelCandidateRepository,
) {

    operator fun invoke(): ModelCandidatesResponse =
        ModelCandidatesResponse.from(modelCandidateRepository.findAllByOrderByDisplayOrder())
}
