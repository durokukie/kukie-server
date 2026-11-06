package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.GatewayApi
import com.duro.kukie.global.security.InternalApi
import com.duro.kukie.serving.application.*
import com.duro.kukie.serving.presentation.dto.request.*
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@InternalApi
@GatewayApi
@RestController
@RequestMapping("/internal/serving-gateway")
class GatewayControlController(private val authorize: AuthorizeGatewayService, private val begin: BeginGatewayUsageService, private val complete: CompleteGatewayUsageService) {
    @PostMapping("/authorize")
    fun authorize(@Valid @RequestBody request: GatewayAuthorizeRequest, response: HttpServletResponse): GatewayIdentity {
        response.setHeader("Cache-Control", "no-store")
        return authorize(request)
    }

    @PostMapping("/requests")
    fun begin(@Valid @RequestBody request: GatewayBeginRequest, response: HttpServletResponse): Map<String, UUID> {
        response.setHeader("Cache-Control", "no-store")
        return mapOf("requestId" to begin(request))
    }

    @PostMapping("/requests/{requestId}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun finish(@PathVariable requestId: UUID, @Valid @RequestBody request: GatewayFinishRequest) = complete(request.toCommand(requestId))
}
