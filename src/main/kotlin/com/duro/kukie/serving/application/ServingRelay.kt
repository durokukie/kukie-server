package com.duro.kukie.serving.application

import com.duro.kukie.global.config.properties.ServingRelayProperties
import com.duro.kukie.serving.application.port.out.CounterTransport
import com.duro.kukie.serving.exception.ServingAccessException
import com.duro.kukie.serving.exception.ServingErrorCode
import com.duro.kukie.serving.presentation.ServingApiErrors
import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ScheduledFuture
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ServingRelay(
    private val transport: CounterTransport,
    private val slots: ServingRelaySlots,
    private val properties: ServingRelayProperties,
    private val events: ApplicationEventPublisher,
) : DisposableBean {
    private val deadlines = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("serving-deadline").factory())
    private val mapper = JsonMapper.builder().build()

    fun start(request: HttpServletRequest, response: HttpServletResponse, target: ServingRelayTarget, body: ByteArray?, model: String?, stream: Boolean) {
        val lease = slots.acquire(target.identity.keyId)
        val exchange = try { transport.create(target.counter, if (body == null) "/models" else "/chat/completions", body) }
        catch (_: Exception) { lease.close(); throw ServingAccessException(ServingErrorCode.SERVING_UPSTREAM_FAILED) }
        val async = try { request.startAsync() } catch (e: Exception) { exchange.cancel(); lease.close(); throw e }
        async.timeout = (properties.totalSeconds + 5) * 1000
        val stopped = AtomicBoolean()
        val timedOut = AtomicBoolean()
        val deadline = AtomicReference<ScheduledFuture<*>?>()
        val worker = Thread.ofVirtual().unstarted {
            val started = Instant.now()
            val timer = System.nanoTime()
            val usage = ServingUsageReader()
            var outcome = "failed"
            var status = 502
            try {
                exchange.execute { upstream ->
                    if (stopped.get()) throw IOException()
                    if (upstream.status != 200) {
                        status = when (upstream.status) { 400 -> 400; 429 -> 429; 404, 503 -> 503; else -> 502 }
                        val code = when (upstream.status) { 400 -> "SERVING_UPSTREAM_BAD_REQUEST"; 429 -> "SERVING_UPSTREAM_RATE_LIMITED"; 404, 503 -> "SERVING_MODEL_UNAVAILABLE"; 401, 403 -> "SERVING_UPSTREAM_AUTH_FAILED"; else -> "SERVING_UPSTREAM_FAILED" }
                        ServingApiErrors.write(response, status, code, "고객 카운터가 요청을 처리하지 못했습니다.")
                        return@execute
                    }
                    val contentType = upstream.contentType?.substringBefore(';')?.trim()?.lowercase()
                    if (contentType != (if (stream) "text/event-stream" else "application/json")) throw IOException()
                    response.status = 200
                    response.setHeader("Cache-Control", "no-store")
                    response.setHeader("X-Accel-Buffering", "no")
                    response.contentType = if (stream) "text/event-stream" else "application/json"
                    if (!stream) {
                        val bytes = upstream.body.readNBytes(MAX_JSON + 1)
                        if (bytes.size > MAX_JSON) throw IOException()
                        val result = if (body == null) filterModels(bytes, target.models) else bytes
                        if (body != null) usage.json(bytes)
                        try { response.outputStream.write(result); response.flushBuffer() } catch (e: IOException) { stopped.set(true); throw e }
                    } else {
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (!stopped.get()) {
                            val size = upstream.body.read(buffer)
                            if (size < 0) break
                            total += size
                            if (total > MAX_STREAM) throw IOException()
                            usage.sse(buffer, size)
                            try { response.outputStream.write(buffer, 0, size); response.flushBuffer() } catch (e: IOException) { stopped.set(true); throw e }
                        }
                        if (stopped.get()) throw IOException()
                    }
                    status = 200
                    outcome = if (stream && !usage.finished) "incomplete" else "completed"
                }
            } catch (failure: Exception) {
                if (generateSequence<Throwable>(failure) { it.cause }.any { it is SocketTimeoutException }) timedOut.set(true)
                status = if (timedOut.get()) 504 else if (stopped.get()) 499 else 502
                outcome = if (timedOut.get()) "timeout" else if (stopped.get()) "cancelled" else "failed"
                runCatching { ServingApiErrors.write(response, status, if (status == 504) "SERVING_UPSTREAM_TIMEOUT" else "SERVING_UPSTREAM_FAILED", "API 중계가 완료되지 않았습니다.") }
            } finally {
                deadline.get()?.cancel(false)
                exchange.cancel()
                lease.close()
                runCatching { events.publishEvent(ServingUsageObservation(target.identity, model, started, (System.nanoTime() - timer) / 1_000_000, status, outcome, usage.inputTokens, usage.outputTokens, body != null && (outcome != "completed" || usage.inputTokens == null || usage.outputTokens == null))) }
                runCatching { async.complete() }
            }
        }
        async.addListener(object : AsyncListener {
            override fun onComplete(event: AsyncEvent) { stopped.set(true); exchange.cancel(); worker.interrupt() }
            override fun onError(event: AsyncEvent) { stopped.set(true); exchange.cancel(); worker.interrupt() }
            override fun onTimeout(event: AsyncEvent) {
                timedOut.set(true); stopped.set(true); exchange.cancel(); worker.interrupt()
                runCatching { ServingApiErrors.write(response, 504, "SERVING_UPSTREAM_TIMEOUT", "API 응답 시간이 초과되었습니다.") }
                runCatching { async.complete() }
            }
            override fun onStartAsync(event: AsyncEvent) {}
        })
        deadline.set(deadlines.schedule({ timedOut.set(true); stopped.set(true); exchange.cancel(); worker.interrupt() }, properties.totalSeconds, TimeUnit.SECONDS))
        worker.start()
    }

    override fun destroy() { deadlines.shutdownNow() }

    private fun filterModels(bytes: ByteArray, models: List<String>): ByteArray {
        val root = mapper.readTree(bytes)
        val data = root?.get("data")?.takeIf { it.isArray } ?: throw IOException()
        val filtered = data.filter { it.get("id")?.asString() in models }
        return mapper.writeValueAsBytes(mapOf("object" to "list", "data" to filtered.map { mapOf("id" to it.get("id").asString(), "object" to "model", "owned_by" to "provider") }))
    }

    private companion object {
        const val MAX_JSON = 8 * 1024 * 1024
        const val MAX_STREAM = 64L * 1024 * 1024
    }
}
