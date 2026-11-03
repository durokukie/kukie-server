package com.duro.kukie.modelcatalog.presentation

import com.duro.kukie.global.docs.ApiErrorResponses
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshFailedException
import com.duro.kukie.modelcatalog.exception.ModelCandidateRefreshTooSoonException
import com.duro.kukie.modelcatalog.presentation.dto.response.ModelCandidatesResponse
import com.duro.kukie.team.exception.NotTeamAdminException
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import java.util.UUID

interface ModelCandidateControllerDocs {

    @Operation(
        summary = "모델 후보 목록",
        description = "GPU 화면에서 고를 허깅페이스 모델 후보를 DB 에서 읽어 줍니다. 허깅페이스를 부르지 않습니다. " +
            "실측한 모델만 verified 가 true 이고 먼저 옵니다. gpus 는 크기로 어림한 값이라 실측이 아니며, " +
            "maxConcurrency 는 measuredGpu 에서 잰 값입니다. sizeGb 는 hf download 가 받는 레포 전체 크기입니다. 아직 갱신한 적이 없으면 generatedAt 이 null 이고 목록이 비어 있습니다.",
    )
    fun getModelCandidates(): ResponseEntity<ModelCandidatesResponse>

    @Operation(
        summary = "모델 후보 갱신",
        description = "허깅페이스 트렌딩 목록을 받아 거른 뒤 DB 를 덮어쓰고 새 목록을 줍니다. 어느 팀이든 관리자면 부를 수 있습니다. " +
            "실패하면 저장된 목록은 그대로이고 바로 다시 시도할 수 있습니다. 성공한 뒤 60초 안에는 다시 갱신할 수 없습니다.",
    )
    @ApiErrorResponses(
        NotTeamAdminException::class,
        ModelCandidateRefreshFailedException::class,
        ModelCandidateRefreshTooSoonException::class,
    )
    fun refreshModelCandidates(userId: UUID): ResponseEntity<ModelCandidatesResponse>
}
