package com.duro.kukie.modelcatalog.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.modelcatalog.application.GetModelCandidatesService
import com.duro.kukie.modelcatalog.application.RefreshModelCandidatesService
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelCandidatesResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/gpu/model-candidates")
class ModelCandidateController(
    private val getModelCandidatesService: GetModelCandidatesService,
    private val refreshModelCandidatesService: RefreshModelCandidatesService,
) : ModelCandidateControllerDocs {

    @GetMapping
    override fun getModelCandidates(): ResponseEntity<ModelCandidatesResponse> {
        return ResponseEntity.ok(getModelCandidatesService())
    }

    @PostMapping("/refresh")
    override fun refreshModelCandidates(
        @AuthUser userId: UUID,
    ): ResponseEntity<ModelCandidatesResponse> {
        return ResponseEntity.ok(refreshModelCandidatesService(userId))
    }
}
