package com.duro.kukie.serving.application

import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import com.duro.kukie.serving.domain.ServingApiKeyRepository
import com.duro.kukie.serving.presentation.dto.response.ServingApiKeyPageResponse
import com.duro.kukie.serving.presentation.dto.response.ServingApiKeyResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetMyServingApiKeysService(private val permission: ServingApiKeyPermission, private val keys: ServingApiKeyRepository) {
    @Transactional(readOnly = true)
    operator fun invoke(accessId: UUID, userId: UUID, page: Int, size: Int): ServingApiKeyPageResponse {
        if (page < 0 || size !in 1..100) throw BusinessException(GlobalErrorCode.BAD_REQUEST)
        permission.requireOwner(accessId, userId)
        val result = keys.findAllByAccessId(accessId, PageRequest.of(page, size, Sort.by("createdAt", "id").descending()))
        return ServingApiKeyPageResponse(result.content.map(ServingApiKeyResponse::of), page, size, result.totalElements, result.totalPages)
    }
}
