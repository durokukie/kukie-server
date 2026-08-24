package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidVerificationCodeException : BusinessException(UserErrorCode.INVALID_VERIFICATION_CODE)
