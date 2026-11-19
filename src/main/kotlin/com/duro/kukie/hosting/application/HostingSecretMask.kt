package com.duro.kukie.hosting.application

/** 로그를 밖으로 보내기 전에 비밀처럼 보이는 값을 *** 로 바꾼다. 모두 잡지는 못하므로 소스 본문은 아예 보내지 않는다. */
object HostingSecretMask {

    private const val MASK = "***"

    private val PRIVATE_KEY = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)")
    // 이름이 이 낱말로 끝나는 값만 가린다. KeyError: 'DATABASE_URL' 처럼 원인을 알려 주는 줄은 남긴다
    private val ASSIGNMENT = Regex(
        "(?i)(\\b[A-Za-z0-9_.-]*(?:key|token|secret|password|passwd|pwd|credentials?|auth)[\"']?\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|\\S+)",
    )
    private val AUTH_HEADER = Regex("(?i)(\\b(?:proxy-)?authorization[\"']?\\s*[=:]\\s*[\"']?(?:[A-Za-z-]+\\s+)?)[^\\s\"']+")
    private val BEARER = Regex("(?i)(bearer\\s+)\\S+")
    // "basic setup" 같은 문장은 남기도록 숫자나 +/= 가 섞인 base64 만 가린다
    private val BASIC = Regex("(?i)(\\bbasic\\s+)(?=[A-Za-z0-9+/]*[0-9+/=])[A-Za-z0-9+/]{8,}={0,2}")
    private val URL_CREDENTIALS = Regex("(://)[^/\\s:@]+:[^/\\s@]+@")
    private val TOKENS = listOf(
        Regex("sk-[A-Za-z0-9_-]{16,}"),
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("xox[abprs]-[A-Za-z0-9-]{10,}"),
        Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
    )

    fun mask(text: String): String {
        var masked = PRIVATE_KEY.replace(text, MASK)
        masked = ASSIGNMENT.replace(masked) { it.groupValues[1] + MASK }
        masked = AUTH_HEADER.replace(masked) { it.groupValues[1] + MASK }
        masked = BEARER.replace(masked) { it.groupValues[1] + MASK }
        masked = BASIC.replace(masked) { it.groupValues[1] + MASK }
        masked = URL_CREDENTIALS.replace(masked) { it.groupValues[1] + "$MASK:$MASK@" }
        return TOKENS.fold(masked) { acc, token -> token.replace(acc, MASK) }
    }
}
