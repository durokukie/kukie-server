package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorCredentials
import com.duro.kukie.serving.domain.ConnectorModelStatus
import com.duro.kukie.serving.domain.ConnectorModelStatuses
import com.duro.kukie.serving.domain.DisabledServingModels
import com.duro.kukie.serving.presentation.dto.request.GatewayModelStatusRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class ReportConnectorModelStatusService(
    private val statuses: ConnectorModelStatuses,
    private val credentials: ConnectorCredentials,
    private val disabled: DisabledServingModels,
) {
    @Transactional
    operator fun invoke(request: GatewayModelStatusRequest) {
        val now = utcNow()
        for (item in request.statuses.orEmpty().filterNotNull()) {
            val checked = item.checkedAt?.takeIf { !it.isAfter(now.plusSeconds(5).toInstant(ZoneOffset.UTC)) && it.isAfter(now.minusSeconds(45).toInstant(ZoneOffset.UTC)) }
                ?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()
            val ready = if (checked != null && item.connected) item.readyModels.orEmpty().filterNotNull().distinct() else emptyList()
            statuses.save(ConnectorModelStatus(item.clusterId, item.tokenSha256, item.connected, checked, now, ready))
            addDiscovered(item.clusterId, item.tokenSha256, ready, now)
        }
    }

    // 클러스터에 새로 뜬 모델을 공개 설정 목록에 올린다. 관리자가 켜기 전까지는 API 요청을 막는다.
    private fun addDiscovered(clusterId: UUID, tokenHash: String, ready: List<String>, now: LocalDateTime) {
        if (ready.isEmpty()) return
        val known = credentials.find(clusterId) ?: return
        if (ready.all { it in known.models }) return
        val credential = credentials.findForUpdate(clusterId) ?: return
        if (credential.tokenHash != tokenHash || !credential.expiresAt.isAfter(now)) return
        val added = ready.filterNot { it in credential.models }.take((MAX_MODELS - credential.models.size).coerceAtLeast(0))
        if (added.isEmpty()) return
        added.forEach { disabled.setEnabled(clusterId, it, false) }
        credentials.save(credential.copy(models = credential.models + added))
    }

    private companion object { const val MAX_MODELS = 100 }
}
