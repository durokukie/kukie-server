package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

/** 쿠키로만 인증된 요청인데 다른 사이트에서 시작됐다(`Sec-Fetch-Site: cross-site`). 헤더 토큰 요청은 해당 없다. */
class CrossSiteCookieException : BusinessException(AuthErrorCode.CROSS_SITE_COOKIE)
