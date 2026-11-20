package com.duro.kukie.hosting.application

import com.duro.kukie.hosting.exception.HostingInvalidLogRangeException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class GetHostingRuntimeLogService(
    private val hostingApps: HostingApps,
) {

    /**
     * 앱이 실행 중에 찍은 로그(배포기가 1분마다 모은 것)를 기간으로 잘라 이어 붙인다. 기본은 최근 1시간이다.
     * 너무 많으면 최근 쪽 10MB 만 주고 첫 줄에 알린다.
     */
    operator fun invoke(userId: UUID, app: String, from: Instant?, to: Instant?): String {
        val owned = hostingApps.requireOwned(userId, app)
        val end = to ?: Instant.now()
        val start = from ?: end.minus(DEFAULT_SPAN)
        if (start >= end || Duration.between(start, end) > MAX_SPAN) throw HostingInvalidLogRangeException()

        val keys = hostingApps.runtimeLogKeys(owned, start, end)
        val chunks = ArrayDeque<String>()
        var bytes = 0
        var cut = keys.size > MAX_FILES
        // 최근 것부터 읽어 상한에서 멈춘다
        for (key in keys.takeLast(MAX_FILES).asReversed()) {
            val lines = hostingApps.readText(key)?.lineSequence()?.filter { it.isNotEmpty() && within(it, start, end) }?.toList().orEmpty()
            if (lines.isEmpty()) continue
            val chunk = lines.joinToString("\n", postfix = "\n")
            if (bytes + chunk.length > MAX_CHARS) {
                cut = true
                break
            }
            chunks.addFirst(chunk)
            bytes += chunk.length
        }
        val body = chunks.joinToString("")
        return if (cut) CUT_NOTICE + body else body
    }

    /** 줄 앞의 시각(Pod 가 여럿이면 "[pod] " 다음)이 기간 안인지. 시각이 없는 줄(배포기의 안내)은 둔다. */
    private fun within(line: String, start: Instant, end: Instant): Boolean {
        val stamp = line.removePrefix(line.takeIf { it.startsWith("[") }?.substringBefore("] ", "")?.let { "$it] " } ?: "").substringBefore(' ')
        val at = runCatching { Instant.parse(stamp) }.getOrNull() ?: return true
        return at >= start && at <= end
    }

    companion object {
        private val DEFAULT_SPAN: Duration = Duration.ofHours(1)
        private val MAX_SPAN: Duration = Duration.ofDays(31)
        private const val MAX_FILES = 3000
        private const val MAX_CHARS = 10 * 1024 * 1024
        private const val CUT_NOTICE = "(이 기간의 로그가 많아 앞부분을 뺐습니다. 기간을 좁혀 다시 받으세요)\n"
    }
}
