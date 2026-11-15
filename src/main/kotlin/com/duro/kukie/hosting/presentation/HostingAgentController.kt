package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.ApproveHostingAgentLoginService
import com.duro.kukie.hosting.application.DenyHostingAgentLoginService
import com.duro.kukie.hosting.application.GetHostingAgentLoginService
import com.duro.kukie.hosting.application.PollHostingAgentTokenService
import com.duro.kukie.hosting.application.StartHostingAgentLoginService
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginRequestResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentLoginResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingAgentPendingResponse
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 코딩 에이전트의 로그인. 에이전트는 curl 로 부르므로 본문은 폼으로 받는다. 허용·거부는 사용자가 웹의 허용 화면에서 한다. */
@RestController
@RequestMapping("/hosting/agent")
class HostingAgentController(
    private val startHostingAgentLoginService: StartHostingAgentLoginService,
    private val pollHostingAgentTokenService: PollHostingAgentTokenService,
    private val getHostingAgentLoginService: GetHostingAgentLoginService,
    private val approveHostingAgentLoginService: ApproveHostingAgentLoginService,
    private val denyHostingAgentLoginService: DenyHostingAgentLoginService,
) : HostingAgentControllerDocs {

    @PostMapping("/login")
    override fun startLogin(): ResponseEntity<HostingAgentLoginResponse> {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(startHostingAgentLoginService())
    }

    @PostMapping("/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun token(@RequestParam pollToken: String): ResponseEntity<Any> {
        val token = pollHostingAgentTokenService(pollToken)
            ?: return ResponseEntity.status(HttpStatus.ACCEPTED).body(HostingAgentPendingResponse())

        // 에이전트가 파일로 받아 curl -H @파일 로 쓴다. 토큰이 화면과 대화 기록에 찍히지 않는다
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_PLAIN).body("Authorization: Bearer $token\n")
    }

    @Authenticated
    @GetMapping("/login/{code}")
    override fun getLogin(@AuthUser userId: UUID, @PathVariable code: String): ResponseEntity<HostingAgentLoginRequestResponse> {
        return ResponseEntity.ok(getHostingAgentLoginService(userId, code))
    }

    @Authenticated
    @PostMapping("/login/{code}/approve")
    override fun approve(@AuthUser userId: UUID, @PathVariable code: String): ResponseEntity<Unit> {
        approveHostingAgentLoginService(userId, code)
        return ResponseEntity.noContent().build()
    }

    @Authenticated
    @PostMapping("/login/{code}/deny")
    override fun deny(@AuthUser userId: UUID, @PathVariable code: String): ResponseEntity<Unit> {
        denyHostingAgentLoginService(userId, code)
        return ResponseEntity.noContent().build()
    }
}
