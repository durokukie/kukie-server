package com.duro.kukie.serving.domain

import java.time.LocalDateTime
import java.util.UUID

data class ConnectorCredential(
    val clusterId: UUID,
    val tokenHash: String,
    val models: List<String>,
    val expiresAt: LocalDateTime,
    val previousTokenHash: String? = null,
    val previousExpiresAt: LocalDateTime? = null,
    val rotatedAt: LocalDateTime? = null,
) {
    fun previousActive(now: LocalDateTime): Boolean = previousTokenHash != null && previousExpiresAt?.isAfter(now) == true
}

interface ConnectorCredentials {
    fun lockCapacity()
    fun find(clusterId: UUID): ConnectorCredential?
    fun findForUpdate(clusterId: UUID): ConnectorCredential?
    fun active(now: LocalDateTime): List<ConnectorCredential>
    fun save(credential: ConnectorCredential)
    fun revoke(clusterId: UUID, now: LocalDateTime)
}
