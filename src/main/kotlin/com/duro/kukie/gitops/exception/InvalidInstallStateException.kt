package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidInstallStateException : BusinessException(GitOpsErrorCode.INVALID_INSTALL_STATE)
