package com.duro.kukie.gitops.infrastructure

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** GitHub 가 주는 PKCS#1(`BEGIN RSA PRIVATE KEY`) PEM 을 JDK 가 읽는 PKCS#8 로 감싸 읽는다. PKCS#8 PEM 도 받는다. */
object GitHubAppPrivateKey {

    private const val PKCS1_LABEL = "RSA PRIVATE KEY"

    // AlgorithmIdentifier { rsaEncryption, NULL }
    private val RSA_ALGORITHM = bytes(0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00)
    private val VERSION_ZERO = bytes(0x02, 0x01, 0x00)

    fun parse(pem: String): PrivateKey {
        val text = pem.replace("\\n", "\n").trim()
        val body = text.lines().filterNot { it.startsWith("-----") }.joinToString("").filterNot(Char::isWhitespace)
        val der = Base64.getDecoder().decode(body)
        val pkcs8 = if (PKCS1_LABEL in text) wrapPkcs1(der) else der

        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    private fun wrapPkcs1(pkcs1: ByteArray): ByteArray =
        der(0x30, VERSION_ZERO + RSA_ALGORITHM + der(0x04, pkcs1))

    private fun der(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + length(content.size) + content

    private fun length(size: Int): ByteArray {
        if (size < 0x80) return byteArrayOf(size.toByte())
        val octets = generateSequence(size) { it ushr 8 }.takeWhile { it > 0 }.map { (it and 0xff).toByte() }.toList().reversed()
        return byteArrayOf((0x80 or octets.size).toByte()) + octets.toByteArray()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
