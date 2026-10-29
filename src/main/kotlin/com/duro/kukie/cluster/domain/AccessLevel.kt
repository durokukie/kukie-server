package com.duro.kukie.cluster.domain

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 템플릿 v5 스택의 AccessLevel. AWS 계정(스택) 하나에 하나다. FULL 은 EKS 클러스터 관리자(클러스터 전체),
 * LIMITED 는 AllowedNamespaces 안의 Edit(v1~v4 와 같다).
 */
enum class AccessLevel(@get:JsonValue val value: String) {
    FULL("full"),
    LIMITED("limited"),
    ;

    companion object {
        fun from(value: String): AccessLevel =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("알 수 없는 권한 레벨: $value")

        /** 스택 알림의 AccessLevel 속성. v1~v4 스택은 보내지 않으므로 없으면 LIMITED, 모르는 값이면 null. */
        fun ofNotice(value: String?): AccessLevel? =
            if (value == null) LIMITED else entries.firstOrNull { it.value == value }
    }
}
