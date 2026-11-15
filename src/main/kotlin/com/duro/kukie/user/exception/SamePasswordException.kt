package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class SamePasswordException : BusinessException(UserErrorCode.SAME_PASSWORD)
