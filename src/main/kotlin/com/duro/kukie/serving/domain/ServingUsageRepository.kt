package com.duro.kukie.serving.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ServingUsageRepository : JpaRepository<ServingUsage, UUID>
