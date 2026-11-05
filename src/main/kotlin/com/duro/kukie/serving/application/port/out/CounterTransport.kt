package com.duro.kukie.serving.application.port.out

import java.io.InputStream

interface CounterTransport {
    fun create(target: CounterTarget, path: String, body: ByteArray?): CounterExchange
}

class CounterTarget(val baseUrl: String, val serviceToken: String) {
    override fun toString() = "CounterTarget([REDACTED])"
}

interface CounterExchange {
    fun execute(consume: (CounterResponse) -> Unit)
    fun cancel()
}

class CounterResponse(val status: Int, val contentType: String?, val body: InputStream)
