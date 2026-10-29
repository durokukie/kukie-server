package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class InvalidRepoUrlException : BusinessException(GitOpsErrorCode.INVALID_REPO_URL)
