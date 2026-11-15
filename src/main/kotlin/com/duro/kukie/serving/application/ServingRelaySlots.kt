package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ServingRelaySlots(private val properties: ServingRelayProperties) {
    private val counts = mutableMapOf<UUID, Int>()
    private var total = 0

    @Synchronized
    fun acquire(keyId: UUID): AutoCloseable {
        val count = counts[keyId] ?: 0
        if (total >= properties.maxConnections || count >= properties.maxConnectionsPerKey) throw ServingAccessException(ServingErrorCode.SERVING_RELAY_BUSY)
        total++
        counts[keyId] = count + 1
        val released = AtomicBoolean()
        return AutoCloseable { if (released.compareAndSet(false, true)) release(keyId) }
    }

    @Synchronized
    private fun release(keyId: UUID) {
        total--
        val count = counts.getValue(keyId) - 1
        if (count == 0) counts.remove(keyId) else counts[keyId] = count
    }
}
