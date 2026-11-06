package com.duro.kukie.serving.infrastructure

import com.duro.kukie.cluster.application.CreateServedModelService
import com.duro.kukie.cluster.application.port.out.EksAccessGateway
import com.duro.kukie.cluster.application.port.out.ModelServingResponse
import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.EksServingTarget
import com.duro.kukie.serving.application.port.out.*
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Component
class KubernetesServingTransport(private val gateway: EksAccessGateway, private val properties: ServingRelayProperties) : EksServingTransport, DisposableBean {
    private val mapper = JsonMapper.builder().build()
    private val watchdog = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("serving-eks-idle").factory())

    override fun create(target: EksServingTarget, body: ByteArray?, model: String?): CounterExchange {
        val closed = AtomicBoolean()
        val response = AtomicReference<ModelServingResponse?>()
        return object : CounterExchange {
            override fun execute(consume: (CounterResponse) -> Unit) {
                if (closed.get()) throw IOException()
                if (body == null) {
                    val bytes = mapper.writeValueAsBytes(mapOf("object" to "list", "data" to target.readyModels.map { mapOf("id" to it, "object" to "model", "owned_by" to "kukie") }))
                    consume(CounterResponse(200, "application/json", ByteArrayInputStream(bytes)))
                    return
                }
                val served = target.models[model]
                if (served == null || served.state.phase != "Ready") {
                    consume(CounterResponse(503, "application/json", ByteArrayInputStream(byteArrayOf())))
                    return
                }
                val payload = (mapper.readTree(body) as? ObjectNode ?: throw IOException()).put("model", served.state.model)
                val credential = target.credential
                val upstream = gateway.openServingResponse(credential.target, credential.region, credential.clusterName,
                    target.endpoint, CreateServedModelService.SERVING_NAMESPACE, served.name, mapper.writeValueAsBytes(payload), Duration.ofSeconds(properties.idleSeconds))
                response.set(upstream)
                if (closed.get()) { upstream.close(); throw IOException() }
                val timedOut = AtomicBoolean()
                val lastRead = AtomicLong(System.nanoTime())
                val idleNanos = TimeUnit.SECONDS.toNanos(properties.idleSeconds)
                val check = watchdog.scheduleAtFixedRate({
                    if (System.nanoTime() - lastRead.get() >= idleNanos && timedOut.compareAndSet(false, true)) {
                        upstream.close()
                    }
                }, properties.idleSeconds * 1000, 100, TimeUnit.MILLISECONDS)
                val input = object : FilterInputStream(upstream.body) {
                    override fun read(): Int = readTimed { super.read() }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = readTimed { `in`.read(bytes, offset, length) }
                    private fun readTimed(read: () -> Int): Int {
                        if (timedOut.get()) throw SocketTimeoutException()
                        val count = try { read() } catch (failure: IOException) {
                            if (timedOut.get()) throw SocketTimeoutException()
                            throw failure
                        }
                        if (timedOut.get()) throw SocketTimeoutException()
                        if (count >= 0) lastRead.set(System.nanoTime())
                        return count
                    }
                }
                try { consume(CounterResponse(upstream.status, upstream.contentType, input)) }
                finally { check.cancel(false); response.getAndSet(null)?.close() }
            }
            override fun cancel() { closed.set(true); response.getAndSet(null)?.close() }
        }
    }
    override fun destroy() { watchdog.shutdownNow() }
}
