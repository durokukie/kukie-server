package com.duro.kukie.notification.exception

import com.duro.kukie.global.exception.BusinessException

class NotificationNotFoundException : BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)
