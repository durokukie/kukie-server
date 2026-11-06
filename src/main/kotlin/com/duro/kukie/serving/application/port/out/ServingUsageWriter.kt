package com.duro.kukie.serving.application.port.out

import com.duro.kukie.serving.application.ServingUsageObservation

interface ServingUsageWriter {
    fun complete(event: ServingUsageObservation)
}
