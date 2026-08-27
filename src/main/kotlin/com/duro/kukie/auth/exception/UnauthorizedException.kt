package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class UnauthorizedException : BusinessException(AuthErrorCode.UNAUTHORIZED)
