package com.duro.kukie.cluster.application.port.out

interface CredentialCipher {

    fun encrypt(credential: Map<String, String>): String

    fun decrypt(encrypted: String): Map<String, String>
}
