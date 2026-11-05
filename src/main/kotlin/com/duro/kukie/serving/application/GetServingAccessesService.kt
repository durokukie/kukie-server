package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingAccessRepository
import com.duro.kukie.serving.presentation.dto.response.ServingAccessResponse
import com.duro.kukie.serving.presentation.dto.response.ServingAccessPageResponse
import com.duro.kukie.global.exception.BusinessException
import com.duro.kukie.global.exception.GlobalErrorCode
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetServingAccessesService(private val permission: ServingAccessPermission, private val accesses: ServingAccessRepository) {
    @Transactional(readOnly = true)
    operator fun invoke(clusterId: UUID, userId: UUID, page: Int, size: Int): ServingAccessPageResponse {
        if (page < 0 || size !in 1..100) throw BusinessException(GlobalErrorCode.BAD_REQUEST)
        permission.requireAdmin(clusterId, userId)
        val now = utcNow()
        val result = accesses.findAllByClusterId(clusterId, PageRequest.of(page, size, Sort.by("createdAt", "id").descending()))
        return ServingAccessPageResponse(
            result.content.map { ServingAccessResponse.of(it, now) }, page, size, result.totalElements, result.totalPages,
        )
    }
}
