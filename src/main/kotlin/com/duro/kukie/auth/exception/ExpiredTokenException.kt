package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class ExpiredTokenException : BusinessException(AuthErrorCode.EXPIRED_TOKEN)