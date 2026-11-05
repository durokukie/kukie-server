package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.GetAdminServingApiKeysService
import com.duro.kukie.serving.application.GetMyServingApiKeysService
import com.duro.kukie.serving.application.IssueServingApiKeyService
import com.duro.kukie.serving.application.RevokeAdminServingApiKeyService
import com.duro.kukie.serving.application.RevokeMyServingApiKeyService
import com.duro.kukie.serving.presentation.dto.request.IssueServingApiKeyRequest
import com.duro.kukie.serving.presentation.dto.response.IssuedServingApiKeyResponse
import com.duro.kukie.serving.presentation.dto.response.ServingApiKeyPageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
class ServingApiKeyController(
    private val issueKey: IssueServingApiKeyService,
    private val getMyKeys: GetMyServingApiKeysService,
    private val getAdminKeys: GetAdminServingApiKeysService,
    private val revokeMyKey: RevokeMyServingApiKeyService,
    private val revokeAdminKey: RevokeAdminServingApiKeyService,
) {
    @PostMapping("/serving/accesses/{accessId}/keys")
    fun issue(@PathVariable accessId: UUID, @AuthUser userId: UUID, @RequestBody @Valid request: IssueServingApiKeyRequest): ResponseEntity<IssuedServingApiKeyResponse> =
        ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store").body(issueKey(request.toCommand(accessId, userId)))

    @GetMapping("/serving/accesses/{accessId}/keys")
    fun mine(@PathVariable accessId: UUID, @AuthUser userId: UUID, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): ServingApiKeyPageResponse =
        getMyKeys(accessId, userId, page, size)

    @DeleteMapping("/serving/accesses/{accessId}/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeMine(@PathVariable accessId: UUID, @PathVariable keyId: UUID, @AuthUser userId: UUID) = revokeMyKey(accessId, userId, keyId)

    @GetMapping("/clusters/{clusterId}/serving/accesses/{accessId}/keys")
    fun admin(@PathVariable clusterId: UUID, @PathVariable accessId: UUID, @AuthUser userId: UUID, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): ServingApiKeyPageResponse =
        getAdminKeys(clusterId, accessId, userId, page, size)

    @DeleteMapping("/clusters/{clusterId}/serving/accesses/{accessId}/keys/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeAdmin(@PathVariable clusterId: UUID, @PathVariable accessId: UUID, @PathVariable keyId: UUID, @AuthUser userId: UUID) = revokeAdminKey(clusterId, accessId, userId, keyId)
}
