package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

/** 코드 없음·만료·재사용·PKCE 불일치를 구분하지 않는다 — 코드를 맞혀 보는 쪽에 힌트가 된다. */
class InvalidHandoffCodeException : BusinessException(AuthErrorCode.INVALID_HANDOFF_CODE)
