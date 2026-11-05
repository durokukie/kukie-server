package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class PasswordNotSetException : BusinessException(UserErrorCode.PASSWORD_NOT_SET)
