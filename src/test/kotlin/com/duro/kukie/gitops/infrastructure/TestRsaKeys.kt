package com.duro.kukie.gitops.infrastructure

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

/** 시험마다 새로 만드는 RSA 키. GitHub 가 주는 것과 같은 PKCS#1 PEM 으로도 낸다. */
object TestRsaKeys {

    val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    // 2048비트 RSA 의 PKCS#8 머리(SEQUENCE · version · AlgorithmIdentifier · OCTET STRING 머리) 26바이트를 떼면 PKCS#1 이다
    private const val PKCS8_HEADER_BYTES = 26

    fun pkcs1Pem(): String = pem("RSA PRIVATE KEY", keyPair.private.encoded.copyOfRange(PKCS8_HEADER_BYTES, keyPair.private.encoded.size))

    fun pkcs8Pem(): String = pem("PRIVATE KEY", keyPair.private.encoded)

    private fun pem(label: String, der: ByteArray): String =
        "-----BEGIN $label-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) +
            "\n-----END $label-----\n"
}
