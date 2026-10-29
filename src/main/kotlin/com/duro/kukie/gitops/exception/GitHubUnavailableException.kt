package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubUnavailableException : BusinessException(GitOpsErrorCode.GITHUB_UNAVAILABLE)
