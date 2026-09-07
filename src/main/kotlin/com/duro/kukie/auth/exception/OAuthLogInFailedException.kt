package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class OAuthLogInFailedException : BusinessException(AuthErrorCode.OAUTH_LOGIN_FAILED)
