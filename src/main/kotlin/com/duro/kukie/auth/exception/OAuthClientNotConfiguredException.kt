package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

/** 요청이 웹 리다이렉트로 왔는데 웹용 OAuth 클라이언트(id/secret)가 설정에 없다. 클라이언트 잘못이 아니라 서버 설정 문제다. */
class OAuthClientNotConfiguredException : BusinessException(AuthErrorCode.OAUTH_CLIENT_NOT_CONFIGURED)
