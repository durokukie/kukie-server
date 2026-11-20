package com.duro.kukie.hosting.application.port.out

interface HostingAnalysisCache {

    fun get(key: String): String?

    fun put(key: String, value: String)
}
