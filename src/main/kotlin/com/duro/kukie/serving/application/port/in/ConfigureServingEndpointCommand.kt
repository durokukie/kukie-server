package com.duro.kukie.serving.application.port.`in`

import java.util.UUID

class ConfigureServingEndpointCommand(val clusterId: UUID, val userId: UUID, val baseUrl: String, val serviceToken: String, val models: List<String>) {
    override fun toString() = "ConfigureServingEndpointCommand(clusterId=$clusterId)"
}
