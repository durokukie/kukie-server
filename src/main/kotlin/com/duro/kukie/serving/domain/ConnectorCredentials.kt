package com.duro.kukie.serving.domain

import java.time.LocalDateTime
import java.util.UUID

data class ConnectorCredential(val clusterId: UUID, val tokenHash: String, val models: List<String>, val expiresAt: LocalDateTime)

interface ConnectorCredentials {
    fun find(clusterId: UUID): ConnectorCredential?
    fun active(now: LocalDateTime): List<ConnectorCredential>
    fun save(credential: ConnectorCredential)
    fun revoke(clusterId: UUID, now: LocalDateTime)
}
