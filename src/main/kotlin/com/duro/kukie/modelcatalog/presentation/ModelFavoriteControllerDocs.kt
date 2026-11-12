package com.duro.kukie.modelcatalog.presentation

import com.duro.kukie.cluster.exception.ClusterNotFoundException
import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.modelcatalog.presentation.dto.request.ModelFavoriteRequest
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelFavoritesResponse
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ModelFavoriteControllerDocs {

    @Operation(
        summary = "모델 후보 즐겨찾기 목록",
        description = "이 클러스터에서 즐겨찾기한 허깅페이스 모델 id 를 넣은 순서로 줍니다. 클러스터를 볼 수 있으면 부를 수 있습니다. " +
            "후보에서 모델을 시작하면 저절로 들어갑니다. 후보 목록 갱신은 즐겨찾기 모델이 다운로드 순위 밖이어도 따로 받아 남깁니다.",
    )
    @ApiErrorResponses(ClusterNotFoundException::class)
    fun getModelFavorites(clusterId: UUID, userId: UUID): ResponseEntity<ModelFavoritesResponse>

    @Operation(
        summary = "모델 후보 즐겨찾기 추가",
        description = "팀 Admin 만 부를 수 있습니다. 이미 있으면 그대로 두고 지금 목록을 줍니다.",
    )
    @ApiErrorResponses(ClusterNotFoundException::class, NotTeamAdminException::class)
    fun addModelFavorite(clusterId: UUID, userId: UUID, request: ModelFavoriteRequest): ResponseEntity<ModelFavoritesResponse>

    @Operation(
        summary = "모델 후보 즐겨찾기 빼기",
        description = "팀 Admin 만 부를 수 있습니다. 모델 id 에 / 가 있어 본문 { model } 으로 받습니다. 없는 모델이어도 지금 목록을 줍니다.",
    )
    @ApiErrorResponses(ClusterNotFoundException::class, NotTeamAdminException::class)
    fun removeModelFavorite(clusterId: UUID, userId: UUID, request: ModelFavoriteRequest): ResponseEntity<ModelFavoritesResponse>
}
