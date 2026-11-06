package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.ServingModelAccess
import com.duro.kukie.serving.application.ServingModelAccessService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Authenticated
@RestController
class ServingModelAccessController(private val service: ServingModelAccessService) {
    @GetMapping("/clusters/{clusterId}/serving/model-access")
    fun list(@PathVariable clusterId: UUID, @AuthUser userId: UUID): List<ServingModelAccess> = service.list(clusterId, userId)

    @PatchMapping("/clusters/{clusterId}/serving/model-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@PathVariable clusterId: UUID, @AuthUser userId: UUID, @RequestBody @Valid request: SetServingModelAccessRequest) =
        service.update(clusterId, userId, request.model, request.enabled!!)
}

data class SetServingModelAccessRequest(
    @field:NotBlank @field:Size(max = 200) @field:Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]*") val model: String,
    @field:NotNull val enabled: Boolean?,
)
