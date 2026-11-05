package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.ConfigureServingEndpointService
import com.duro.kukie.serving.application.DeleteServingEndpointService
import com.duro.kukie.serving.application.GetServingEndpointService
import com.duro.kukie.serving.application.ServingEndpointResponse
import com.duro.kukie.serving.presentation.dto.request.ConfigureServingEndpointRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
class ServingEndpointController(private val configure: ConfigureServingEndpointService, private val getEndpoint: GetServingEndpointService, private val deleteEndpoint: DeleteServingEndpointService) {
    @PutMapping("/clusters/{clusterId}/serving/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun put(@PathVariable clusterId: UUID, @AuthUser userId: UUID, @RequestBody @Valid request: ConfigureServingEndpointRequest) = configure(request.toCommand(clusterId, userId))

    @GetMapping("/clusters/{clusterId}/serving/endpoint")
    fun get(@PathVariable clusterId: UUID, @AuthUser userId: UUID): ServingEndpointResponse? = getEndpoint(clusterId, userId)

    @DeleteMapping("/clusters/{clusterId}/serving/endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable clusterId: UUID, @AuthUser userId: UUID) = deleteEndpoint(clusterId, userId)
}
