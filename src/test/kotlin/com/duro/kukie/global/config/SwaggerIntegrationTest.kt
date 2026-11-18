package com.duro.kukie.global.config

import com.duro.kukie.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class SwaggerIntegrationTest : IntegrationTest() {

    @Test
    fun `API 명세를 만든다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths") { isNotEmpty() }
        }
    }
}
