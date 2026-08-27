package com.duro.kukie.user.exception

import com.duro.kukie.global.exception.BusinessException

class DuplicatedEmailException : BusinessException(UserErrorCode.DUPLICATED_EMAIL)
