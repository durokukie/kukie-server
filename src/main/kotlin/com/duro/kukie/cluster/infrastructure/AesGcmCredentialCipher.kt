package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.out.CredentialCipher
import com.duro.kukie.global.config.properties.ClusterProperties
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesGcmCredentialCipher(
    clusterProperties: ClusterProperties,
) : CredentialCipher {

    private val key = SecretKeySpec(decodeKey(clusterProperties.credentialKey), "AES")
    private val random = SecureRandom()
    private val jsonMapper = JsonMapper.builder().build()

    override fun encrypt(credential: Map<String, String>): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(jsonMapper.writeValueAsBytes(credential.toSortedMap()))

        return Base64.getEncoder().encodeToString(nonce + sealed)
    }

    override fun decrypt(encrypted: String): Map<String, String> {
        val plain = try {
            val bytes = Base64.getDecoder().decode(encrypted)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
            cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        } catch (exception: GeneralSecurityException) {
            throw IllegalStateException(UNREADABLE, exception)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException(UNREADABLE, exception)
        }

        return jsonMapper.readValue(plain, CREDENTIAL_TYPE)
    }

    private fun decodeKey(encoded: String): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(encoded.trim()) }.getOrDefault(ByteArray(0))
        check(bytes.size == KEY_BYTES) { "Credential key must be base64 of exactly 32 bytes" }
        return bytes
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val UNREADABLE = "Stored credential cannot be decrypted"
        private val CREDENTIAL_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
