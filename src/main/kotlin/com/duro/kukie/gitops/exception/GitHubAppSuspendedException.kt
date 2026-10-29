package com.duro.kukie.gitops.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubAppSuspendedException : BusinessException(GitOpsErrorCode.GITHUB_APP_SUSPENDED)
