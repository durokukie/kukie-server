package com.duro.kukie.auth.exception

import com.duro.kukie.global.exception.BusinessException

class GitHubAccountTakenException : BusinessException(AuthErrorCode.GITHUB_ACCOUNT_TAKEN)
