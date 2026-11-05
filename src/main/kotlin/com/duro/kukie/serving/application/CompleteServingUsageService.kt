package com.duro.kukie.serving.application

import com.duro.kukie.serving.application.port.out.ServingUsageWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CompleteServingUsageService(private val writer: ServingUsageWriter) {
    @Transactional
    operator fun invoke(event: ServingUsageObservation) = writer.complete(event)
}
