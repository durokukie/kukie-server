package com.duro.kukie.serving.domain

import java.time.LocalDateTime
import java.util.UUID

data class ConnectorModelStatus(val clusterId: UUID, val tokenHash: String, val connected: Boolean, val checkedAt: LocalDateTime?, val receivedAt: LocalDateTime, val readyModels: List<String>)
interface ConnectorModelStatuses {
    fun find(clusterId: UUID): ConnectorModelStatus?
    fun save(status: ConnectorModelStatus)
}
