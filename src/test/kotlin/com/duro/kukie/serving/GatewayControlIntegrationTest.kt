package com.duro.kukie.serving

import com.duro.kukie.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@TestPropertySource(properties = ["internal.gateway-token=test-gateway-token"])
class GatewayControlIntegrationTest : IntegrationTest() {
    @Test
    fun `지운 옛 게이트웨이 API는 게이트웨이 자격으로도 찾을 수 없다`() {
        // when then
        for (path in listOf("/authorize", "/requests", "/requests/${UUID.randomUUID()}/finish")) {
            mockMvc.post("/internal/serving-gateway$path") {
                header("X-Kukie-Internal-Token", "test-gateway-token"); contentType = MediaType.APPLICATION_JSON; content = "{}"
            }.andExpect { status { isNotFound() } }
        }
    }

    @Test
    fun `게이트웨이 자격으로 다른 내부 API를 부를 수 없다`() {
        // when then
        mockMvc.patch("/internal/clusters/${UUID.randomUUID()}/status") {
            header("X-Kukie-Internal-Token", "test-gateway-token")
            contentType = MediaType.APPLICATION_JSON; content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }
}
