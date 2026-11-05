package com.duro.kukie.serving.presentation

import jakarta.servlet.http.HttpServletResponse
import tools.jackson.databind.json.JsonMapper

object ServingApiErrors {
    private val mapper = JsonMapper.builder().build()
    fun write(response: HttpServletResponse, status: Int, code: String, message: String) {
        if (response.isCommitted) return
        response.resetBuffer()
        response.status = status
        response.contentType = "application/json"
        response.setHeader("Cache-Control", "no-store")
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer")
        response.outputStream.write(mapper.writeValueAsBytes(mapOf("error" to mapOf("message" to message, "type" to "kukie_error", "code" to code))))
    }
}
