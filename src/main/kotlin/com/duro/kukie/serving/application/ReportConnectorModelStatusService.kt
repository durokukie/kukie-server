package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ConnectorModelStatus
import com.duro.kukie.serving.domain.ConnectorModelStatuses
import com.duro.kukie.serving.presentation.dto.request.GatewayModelStatusRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class ReportConnectorModelStatusService(private val statuses: ConnectorModelStatuses) {
    @Transactional
    operator fun invoke(request: GatewayModelStatusRequest) {
        val now = utcNow()
        for (item in request.statuses.orEmpty().filterNotNull()) {
            val checked = item.checkedAt?.takeIf { !it.isAfter(now.plusSeconds(5).toInstant(ZoneOffset.UTC)) && it.isAfter(now.minusSeconds(45).toInstant(ZoneOffset.UTC)) }
                ?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()
            statuses.save(ConnectorModelStatus(item.clusterId, item.tokenSha256, item.connected, checked, now,
                if (checked != null && item.connected) item.readyModels.orEmpty().filterNotNull().distinct() else emptyList()))
        }
    }
}
