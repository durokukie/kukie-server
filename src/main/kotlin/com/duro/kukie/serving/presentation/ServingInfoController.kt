package com.duro.kukie.serving.presentation

import com.duro.kukie.global.security.AuthUser
import com.duro.kukie.global.security.Authenticated
import com.duro.kukie.serving.application.GetAdminServingInfoService
import com.duro.kukie.serving.application.GetMyServingInfoService
import com.duro.kukie.serving.application.ServingInfoResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Authenticated
@RestController
class ServingInfoController(private val getMine: GetMyServingInfoService, private val getAdmin: GetAdminServingInfoService) {
    @GetMapping("/serving/accesses/{accessId}/info")
    fun mine(@PathVariable accessId: UUID, @AuthUser userId: UUID): ServingInfoResponse = getMine(accessId, userId)
    @GetMapping("/clusters/{clusterId}/serving/info")
    fun admin(@PathVariable clusterId: UUID, @AuthUser userId: UUID): ServingInfoResponse = getAdmin(clusterId, userId)
}
