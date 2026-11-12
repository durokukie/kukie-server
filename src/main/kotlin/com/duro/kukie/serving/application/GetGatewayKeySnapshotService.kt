package com.duro.kukie.serving.application

import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.application.port.out.GatewayKeySnapshots
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class GatewayKeySnapshotKey(val tokenSha256: String, val keyId: UUID, val clusterId: UUID, val expiresAt: Instant) {
    override fun toString() = "GatewayKeySnapshotKey(keyId=$keyId, clusterId=$clusterId)"
}

data class GatewayKeySnapshot(val keys: List<GatewayKeySnapshotKey>, val clusterModels: Map<UUID, List<String>>)

@Service
class GetGatewayKeySnapshotService(private val snapshots: GatewayKeySnapshots, private val permission: GatewayKeyPermission) {
    @Transactional(readOnly = true)
    operator fun invoke(): GatewayKeySnapshot {
        val rows = snapshots.usable(utcNow(), MAX_KEYS + 1)
        if (rows.size > MAX_KEYS) throw ServingAccessException(ServingErrorCode.SERVING_KEY_SNAPSHOT_TOO_LARGE)
        val keys = rows.map { GatewayKeySnapshotKey(it.tokenHash, it.keyId, it.clusterId, it.expiresAt.toInstant(ZoneOffset.UTC)) }
        return GatewayKeySnapshot(keys, keys.map { it.clusterId }.distinct().associateWith { permission.models(it) })
    }

    companion object { const val MAX_KEYS = 50_000 }
}
