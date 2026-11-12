package com.duro.kukie.cluster.domain

/** Kukie 가 받는 namespace 이름. 고객 템플릿이 정확한 이름만 허용하므로 패턴(*·?)과 시스템 namespace(kube-*)는 받지 않는다. */
object NamespaceName {
    private const val MAX_LENGTH = 63
    private const val SYSTEM_PREFIX = "kube-"
    private val DNS_LABEL = Regex("[a-z0-9]([-a-z0-9]*[a-z0-9])?")

    fun isAllowed(namespace: String): Boolean =
        namespace.length <= MAX_LENGTH && DNS_LABEL.matches(namespace) && !namespace.startsWith(SYSTEM_PREFIX)
}
