package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingUploadTooLargeException(message: String = HostingErrorCode.HOSTING_UPLOAD_TOO_LARGE.message) : BusinessException(HostingErrorCode.HOSTING_UPLOAD_TOO_LARGE, message)
