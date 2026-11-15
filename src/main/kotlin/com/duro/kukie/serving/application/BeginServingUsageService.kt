package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.global.util.utcNow
import com.duro.kukie.serving.domain.ServingUsage
import com.duro.kukie.serving.domain.ServingUsageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BeginServingUsageService(private val usage: ServingUsageRepository, private val properties: ServingRelayProperties) {
    @Transactional
    operator fun invoke(identity: AuthenticatedServingKey, model: String?): UUID {
        val now = utcNow()
        return usage.saveAndFlush(ServingUsage(identity.keyId, identity.accessId, identity.clusterId, identity.userId, model, now, now.plusSeconds(properties.totalSeconds + 60))).id
    }
}
