package com.duro.kukie.cluster.infrastructure

import java.net.InetAddress

fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>
}
