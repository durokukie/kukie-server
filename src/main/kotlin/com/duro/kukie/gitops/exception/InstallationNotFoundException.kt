package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class InstallationNotFoundException : BusinessException(GitOpsErrorCode.INSTALLATION_NOT_FOUND)
