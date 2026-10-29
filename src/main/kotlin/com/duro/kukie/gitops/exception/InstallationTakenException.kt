package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class InstallationTakenException : BusinessException(GitOpsErrorCode.INSTALLATION_TAKEN)
