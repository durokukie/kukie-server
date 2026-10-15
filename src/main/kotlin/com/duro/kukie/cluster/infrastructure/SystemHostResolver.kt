package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.exception.KubeconfigRejectedException
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class SystemHostResolver : HostResolver, DisposableBean {

    private val lookUp: (String) -> List<InetAddress>
    private val timeout: Duration
    private val executor: ExecutorService

    constructor() : this(::lookUpSystem, TIMEOUT, WORKERS)

    internal constructor(lookUp: (String) -> List<InetAddress>, timeout: Duration, workers: Int) {
        this.lookUp = lookUp
        this.timeout = timeout
        this.executor = Executors.newFixedThreadPool(workers, Thread.ofPlatform().name("host-resolver-", 0).daemon().factory())
    }

    override fun resolve(host: String): List<InetAddress> {
        legacyIpv4(host)?.let { return listOf(it) }

        val lookup = executor.submit<List<InetAddress>> { lookUp(host) }
        return try {
            lookup.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            lookup.cancel(true)
            throw KubeconfigRejectedException("클러스터 주소의 이름을 ${timeout.toSeconds()}초 안에 확인하지 못했습니다: $host")
        } catch (exception: ExecutionException) {
            throw exception.cause ?: exception
        }
    }

    override fun destroy() {
        executor.shutdownNow()
    }

    /** JDK 가 glibc 와 다르게 읽는 옛 IPv4 표기(127.1, 0177.0.0.1 등)를 직접 읽어야 주소 검사가 새지 않는다. */
    private fun legacyIpv4(host: String): InetAddress? {
        val parts = host.split('.')
        if (parts.size > 4 || parts.any { !LEGACY_PART.matches(it) }) return null
        val values = parts.map { it.toLegacyNumber() ?: return null }

        val lastMax = (1L shl (8 * (5 - parts.size))) - 1
        if (values.dropLast(1).any { it > 255 } || values.last() > lastMax) return null
        val address = values.dropLast(1)
            .foldIndexed(values.last()) { index, acc, value -> acc or (value shl (8 * (3 - index))) }

        return InetAddress.getByAddress(ByteArray(4) { (address shr (8 * (3 - it))).toByte() })
    }

    private fun String.toLegacyNumber(): Long? = when {
        startsWith("0x", ignoreCase = true) -> drop(2).ifEmpty { "0" }.toLongOrNull(16)
        length > 1 && startsWith("0") -> toLongOrNull(8)
        else -> toLongOrNull()
    }

    companion object {
        private val LEGACY_PART = Regex("0[xX][0-9a-fA-F]*|[0-9]+")

        private const val WORKERS = 4

        private val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}

private fun lookUpSystem(host: String): List<InetAddress> =
    try {
        InetAddress.getAllByName(host).toList()
    } catch (_: UnknownHostException) {
        emptyList()
    }
