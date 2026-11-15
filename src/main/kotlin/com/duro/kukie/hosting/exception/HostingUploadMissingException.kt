package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException

class HostingUploadMissingException(message: String = HostingErrorCode.HOSTING_UPLOAD_MISSING.message) : BusinessException(HostingErrorCode.HOSTING_UPLOAD_MISSING, message)
