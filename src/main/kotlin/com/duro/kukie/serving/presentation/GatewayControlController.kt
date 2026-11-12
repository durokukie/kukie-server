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
class GatewayControlController(private val authorize: AuthorizeGatewayService, private val begin: BeginGatewayUsageService, private val complete: CompleteGatewayUsageService, private val connectors: GetConnectorBindingsService, private val reportModels: ReportConnectorModelStatusService, private val renewConnector: RenewConnectorCredentialService, private val keySnapshot: GetGatewayKeySnapshotService, private val recordUsage: RecordGatewayUsageService) {
    @PostMapping("/model-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun modelStatus(@Valid @RequestBody request: GatewayModelStatusRequest) = reportModels(request)

    @PostMapping("/connector-bindings")
    fun connectorBindings(response: HttpServletResponse): Map<String, List<ConnectorBinding>> {
        response.setHeader("Cache-Control", "no-store")
        return connectors()
    }

    @PostMapping("/key-snapshot")
    fun keySnapshot(response: HttpServletResponse): GatewayKeySnapshot {
        response.setHeader("Cache-Control", "no-store")
        return keySnapshot()
    }

    @PostMapping("/usage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun usage(@Valid @RequestBody request: GatewayUsageRequest) = recordUsage(request)

    @PostMapping("/connector-credential/renew")
    fun renewConnectorCredential(@Valid @RequestBody request: GatewayRenewConnectorCredentialRequest, response: HttpServletResponse): RenewedConnectorCredential {
        response.setHeader("Cache-Control", "no-store")
        return renewConnector(request.toCommand())
    }

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
