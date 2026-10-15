package com.duro.kukie.cluster.infrastructure

import java.net.InetAddress

object BlockedAddresses {

    private val IPV4 = cidrs(
        "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24",
        "192.0.2.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "240.0.0.0/4",
        "255.255.255.255/32",
    )

    private val IPV6 = cidrs(
        "::/8", "100::/8", "200::/7", "400::/6", "800::/5", "1000::/4", "2001::/23", "2001:db8::/32", "2002::/16",
        "3fff::/20", "4000::/3", "6000::/3", "8000::/3", "a000::/3", "c000::/3", "e000::/4", "f000::/5", "f800::/6",
        "fc00::/7", "fe00::/9", "fe80::/10",
    )

    fun contains(address: InetAddress): Boolean {
        val bytes = address.address.let { if (it.isIpv4Mapped()) it.copyOfRange(12, 16) else it }
        val ranges = if (bytes.size == 4) IPV4 else IPV6
        return ranges.any { it.contains(bytes) }
    }

    private fun ByteArray.isIpv4Mapped(): Boolean =
        size == 16 && (0 until 10).all { this[it] == 0.toByte() } && this[10] == 0xff.toByte() && this[11] == 0xff.toByte()

    private fun cidrs(vararg values: String): List<Cidr> = values.map { Cidr.parse(it) }

    private class Cidr(private val network: ByteArray, private val prefixLength: Int) {

        fun contains(address: ByteArray): Boolean {
            if (address.size != network.size) return false
            val fullBytes = prefixLength / 8
            if ((0 until fullBytes).any { address[it] != network[it] }) return false
            val remainingBits = prefixLength % 8
            if (remainingBits == 0) return true
            val mask = (0xff shl (8 - remainingBits)) and 0xff
            return (address[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
        }

        companion object {
            fun parse(value: String): Cidr {
                val (address, prefix) = value.split('/')
                return Cidr(InetAddress.getByName(address).address, prefix.toInt())
            }
        }
    }
}
