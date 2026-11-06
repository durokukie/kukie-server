package com.duro.kukie.serving.application.port.out

import com.duro.kukie.serving.application.EksServingTarget

interface EksServingTransport {
    fun create(target: EksServingTarget, body: ByteArray?, model: String?): CounterExchange
}
