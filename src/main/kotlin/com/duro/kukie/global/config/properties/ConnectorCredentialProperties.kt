package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 커넥터 자격의 수명, 회전 뒤 직전 자격을 받아 주는 겹침 기간, 클러스터별 최소 회전 간격. */
@ConfigurationProperties(prefix = "serving.connector-credential")
data class ConnectorCredentialProperties(
    val lifetime: Duration = Duration.ofHours(24),
    val overlap: Duration = Duration.ofMinutes(30),
    val renewMinInterval: Duration = Duration.ofSeconds(30),
) {
    init {
        // 게이트웨이는 25시간이 넘는 만료를 거부한다.
        require(lifetime > Duration.ZERO && lifetime <= Duration.ofHours(24)) { "Connector credential lifetime must be within 24 hours" }
        require(overlap > Duration.ZERO && overlap < lifetime) { "Connector credential overlap must be shorter than its lifetime" }
        require(!renewMinInterval.isNegative && renewMinInterval < overlap) { "Connector renew interval must be shorter than the overlap" }
    }
}
