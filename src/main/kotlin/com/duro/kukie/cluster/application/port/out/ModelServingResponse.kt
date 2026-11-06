package com.duro.kukie.cluster.application.port.out

import java.io.InputStream

data class NamedServingModel(val name: String, val state: ServedModelState)

interface ModelServingResponse : AutoCloseable {
    val status: Int
    val contentType: String?
    val body: InputStream
    override fun close()
}
