package com.duro.kukie.hosting.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.hosting.application.GetHostingDbExportsService
import com.duro.kukie.hosting.application.StartHostingDbExportService
import com.duro.kukie.hosting.presentation.dto.response.HostingCommandResponse
import com.duro.kukie.hosting.presentation.dto.response.HostingDbExportsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
@RequestMapping("/hosting/db/exports")
class HostingDbExportController(
    private val startHostingDbExportService: StartHostingDbExportService,
    private val getHostingDbExportsService: GetHostingDbExportsService,
) : HostingDbExportControllerDocs {

    @PostMapping
    override fun start(@AuthUser userId: UUID): ResponseEntity<HostingCommandResponse> {
        return ResponseEntity.accepted().body(startHostingDbExportService(userId))
    }

    @GetMapping
    override fun getExports(@AuthUser userId: UUID): ResponseEntity<HostingDbExportsResponse> {
        return ResponseEntity.ok(getHostingDbExportsService(userId))
    }
}
