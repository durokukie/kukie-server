package com.duro.kukie.modelcatalog.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.modelcatalog.application.ModelFavoriteService
import com.duro.kukie.modelcatalog.presentation.dto.request.ModelFavoriteRequest
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelFavoritesResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/clusters/{clusterId}/gpu/model-favorites")
class ModelFavoriteController(
    private val modelFavoriteService: ModelFavoriteService,
) : ModelFavoriteControllerDocs {

    @GetMapping
    override fun getModelFavorites(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
    ): ResponseEntity<ModelFavoritesResponse> {
        return ResponseEntity.ok(modelFavoriteService.list(clusterId, userId))
    }

    @PutMapping
    override fun addModelFavorite(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: ModelFavoriteRequest,
    ): ResponseEntity<ModelFavoritesResponse> {
        return ResponseEntity.ok(modelFavoriteService.add(clusterId, userId, request.model))
    }

    /** 모델 id 에 / 가 있어 경로가 아니라 본문으로 받는다. */
    @DeleteMapping
    override fun removeModelFavorite(
        @PathVariable clusterId: UUID,
        @AuthUser userId: UUID,
        @RequestBody @Valid request: ModelFavoriteRequest,
    ): ResponseEntity<ModelFavoritesResponse> {
        return ResponseEntity.ok(modelFavoriteService.remove(clusterId, userId, request.model))
    }
}
