package com.duro.kukie.hosting.exception

import com.duro.kukie.global.exception.BusinessException
import java.time.LocalDate

class HostingAccountTooNewException(
    message: String = HostingErrorCode.HOSTING_ACCOUNT_TOO_NEW.message,
) : BusinessException(HostingErrorCode.HOSTING_ACCOUNT_TOO_NEW, message) {

    /** eligibleOn 은 신청할 수 있게 되는 날(한국 시각). */
    constructor(eligibleOn: LocalDate) : this(
        "${HostingErrorCode.HOSTING_ACCOUNT_TOO_NEW.message} 이 계정은 ${eligibleOn}부터 신청할 수 있습니다.",
    )
}
