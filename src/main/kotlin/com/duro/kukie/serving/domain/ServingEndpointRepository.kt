package com.duro.kukie.serving.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ServingEndpointRepository : JpaRepository<ServingEndpoint, UUID> {
    fun findByClusterId(clusterId: UUID): ServingEndpoint?
}
