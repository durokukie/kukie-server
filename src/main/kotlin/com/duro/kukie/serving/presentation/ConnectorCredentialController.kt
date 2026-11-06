package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.presentation.dto.request.IssueConnectorCredentialRequest
import com.duro.kukie.serving.application.IssueConnectorCredentialService
import com.duro.kukie.serving.application.IssuedConnectorCredential
import com.duro.kukie.serving.application.RevokeConnectorCredentialService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID


@Authenticated
@RestController
class ConnectorCredentialController(private val issue: IssueConnectorCredentialService, private val revokeCredential: RevokeConnectorCredentialService) {
    @PostMapping("/clusters/{clusterId}/serving/connector-credential")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(@PathVariable clusterId: UUID, @AuthUser userId: UUID, @RequestBody @Valid request: IssueConnectorCredentialRequest, response: HttpServletResponse): IssuedConnectorCredential {
        response.setHeader("Cache-Control", "no-store")
        return issue(request.toCommand(clusterId, userId))
    }

    @DeleteMapping("/clusters/{clusterId}/serving/connector-credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable clusterId: UUID, @AuthUser userId: UUID) = revokeCredential(clusterId, userId)
}
