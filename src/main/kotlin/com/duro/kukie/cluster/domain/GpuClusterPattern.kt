package com.duro.kukie.cluster.domain

/** 템플릿 v6 의 GpuClusterPattern. IAM 이 ARN 을 맞출 때와 같이 *, ? 만 와일드카드이고 대소문자를 가린다. */
object GpuClusterPattern {

    private const val ANY = '*'
    private const val ONE = '?'

    // 템플릿의 AllowedPattern·MaxLength 와 같다. 스택 알림은 누구나 보낼 수 있어 다시 거른다
    private val ALLOWED = Regex("^[-_*?0-9A-Za-z]{1,100}$")

    /** 스택 알림 값. 없거나 비었거나 규칙에 맞지 않으면 null. */
    fun ofNotice(value: String?): String? = value?.takeIf(ALLOWED::matches)

    /** 정규식으로 바꾸면 * 가 많은 패턴에서 되돌아가기가 터지므로 직접 맞춘다. */
    fun matches(pattern: String, clusterName: String): Boolean {
        if (pattern.isEmpty()) return false

        var patternAt = 0
        var nameAt = 0
        var lastAny = -1
        var nameAtLastAny = 0
        while (nameAt < clusterName.length) {
            val expected = pattern.getOrNull(patternAt)
            when {
                expected == ANY -> {
                    lastAny = patternAt++
                    nameAtLastAny = nameAt
                }
                expected == ONE || expected == clusterName[nameAt] -> {
                    patternAt++
                    nameAt++
                }
                lastAny >= 0 -> {
                    patternAt = lastAny + 1
                    nameAt = ++nameAtLastAny
                }
                else -> return false
            }
        }
        return pattern.drop(patternAt).all { it == ANY }
    }
}
