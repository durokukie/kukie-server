package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

/** 쿠키 인증 요청의 출처(`Sec-Fetch-Site`)가 우리 페이지가 아니다. */
class CrossSiteCookieException : BusinessException(AuthErrorCode.CROSS_SITE_COOKIE)
