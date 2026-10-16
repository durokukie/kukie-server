package com.duro.kukie.chat.exception

import com.duro.kukie.global.exception.BusinessException

class ChatNotFoundException : BusinessException(ChatErrorCode.NOT_FOUND)
