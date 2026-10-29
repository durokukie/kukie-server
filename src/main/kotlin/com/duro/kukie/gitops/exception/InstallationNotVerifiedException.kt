package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class InstallationNotVerifiedException : BusinessException(GitOpsErrorCode.INSTALLATION_NOT_VERIFIED)
