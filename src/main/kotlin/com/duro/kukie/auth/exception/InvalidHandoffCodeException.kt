package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

/**
 * 앱 넘겨주기 코드가 없거나(만료·재사용) PKCE 검증값이 맞지 않는다. 어느 쪽인지 가르지 않는다 —
 * 코드를 맞혀 보는 쪽에 "코드는 맞았다" 는 힌트가 되기 때문이다.
 */
class InvalidHandoffCodeException : BusinessException(AuthErrorCode.INVALID_HANDOFF_CODE)
