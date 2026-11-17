package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.JoinHostingService
import com.duro.kukie.hosting.presentation.dto.request.JoinHostingRequest
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/hosting/membership")
class HostingMembershipController(
    private val joinHostingService: JoinHostingService,
) : HostingMembershipControllerDocs {

    @PostMapping
    override fun join(@AuthUser userId: UUID, @RequestBody @Valid request: JoinHostingRequest): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(joinHostingService(request.toCommand(userId)))
    }
}
