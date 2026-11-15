package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.GetAdminServingUsageOverviewService
import com.duro.kukie.serving.application.GetAdminServingUsageService
import com.duro.kukie.serving.application.GetMyServingUsageService
import com.duro.kukie.serving.presentation.dto.response.ServingUsageOverviewResponse
import com.duro.kukie.serving.presentation.dto.response.ServingUsageResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Authenticated
@RestController
class ServingUsageController(private val getMine: GetMyServingUsageService, private val getAdmin: GetAdminServingUsageService, private val getOverview: GetAdminServingUsageOverviewService) {
    @GetMapping("/serving/accesses/{accessId}/usage")
    fun mine(@PathVariable accessId: UUID, @AuthUser userId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ServingUsageResponse = getMine(accessId, userId, from, to)

    @GetMapping("/clusters/{clusterId}/serving/usage")
    fun admin(@PathVariable clusterId: UUID, @AuthUser userId: UUID,
        @RequestParam(required = false) user: UUID?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ServingUsageResponse = getAdmin(clusterId, userId, user, from, to)

    @GetMapping("/clusters/{clusterId}/serving/usage/overview")
    fun overview(@PathVariable clusterId: UUID, @AuthUser userId: UUID, @RequestParam(required = false) model: String?): ServingUsageOverviewResponse =
        getOverview(clusterId, userId, model)
}
