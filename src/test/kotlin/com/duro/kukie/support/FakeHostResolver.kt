package com.duro.kukie.support

import com.duro.kukie.cluster.infrastructure.HostResolver
import java.net.InetAddress

class FakeHostResolver : HostResolver, Resettable {

    var addresses: List<InetAddress> = DEFAULT_ADDRESSES

    var onResolve: () -> Unit = {}

    override fun resolve(host: String): List<InetAddress> {
        onResolve()
        return addresses
    }

    override fun clear() {
        addresses = DEFAULT_ADDRESSES
        onResolve = {}
    }

    companion object {
        private val DEFAULT_ADDRESSES = listOf(InetAddress.getByName("93.184.216.34"))
    }
}
