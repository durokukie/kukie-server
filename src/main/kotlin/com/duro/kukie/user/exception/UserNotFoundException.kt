package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class UserNotFoundException : BusinessException(UserErrorCode.USER_NOT_FOUND)
