package com.duro.kukie.global.exception

import com.duro.kukie.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch

class GlobalExceptionHandlerIntegrationTest : IntegrationTest() {

    @Test
    fun `존재하지 않는 경로를 요청하면 404를 반환한다`() {
        mockMvc.get("/not-exist").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GlobalErrorCode.NOT_FOUND.code) }
        }
    }

    @Test
    fun `허용되지 않는 HTTP 메서드로 요청하면 405를 반환한다`() {
        mockMvc.patch("/auth/login").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value(GlobalErrorCode.METHOD_NOT_ALLOWED.code) }
        }
    }
}
