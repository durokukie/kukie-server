package com.duro.kukie.cluster.application.port.out

enum class NamespaceStatus {
    EXISTS,
    MISSING,

    /** 401·403 만 받았다. 막 붙인 access policy 가 API 서버에 아직 퍼지지 않은 동안 이렇게 보인다. */
    GRANT_PENDING,

    /** 그 밖의 응답이나 네트워크 오류로 확인하지 못했다. */
    UNKNOWN,
}
