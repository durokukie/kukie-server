package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.ListHostingAgentKeysService
import com.duro.kukie.hosting.application.RevokeHostingAgentKeyService
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentKeyResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 화면에서 연결한 에이전트를 보고 끊는다. 에이전트 키로는 부를 수 없다(@HostingAgentToken 없음). */
@Authenticated
@RestController
@RequestMapping("/hosting/agent-keys")
class HostingAgentKeyController(
    private val listHostingAgentKeysService: ListHostingAgentKeysService,
    private val revokeHostingAgentKeyService: RevokeHostingAgentKeyService,
) : HostingAgentKeyControllerDocs {

    @GetMapping
    override fun list(@AuthUser userId: UUID): ResponseEntity<List<HostingAgentKeyResponse>> {
        return ResponseEntity.ok(listHostingAgentKeysService(userId))
    }

    @DeleteMapping("/{keyId}")
    override fun revoke(@AuthUser userId: UUID, @PathVariable keyId: UUID): ResponseEntity<Unit> {
        revokeHostingAgentKeyService(userId, keyId)
        return ResponseEntity.noContent().build()
    }
}
