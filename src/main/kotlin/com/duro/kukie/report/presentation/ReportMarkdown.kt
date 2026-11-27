package com.duro.kukie.report.presentation

import com.duro.kukie.actionplan.domain.PlanStatus
import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.presentation.WorklogMarkdown
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import com.duro.kukie.report.application.resultLabel
import com.duro.kukie.report.application.port.out.ClusterMetrics
import com.duro.kukie.report.application.port.out.ClusterMetricsResult
import com.duro.kukie.report.application.port.out.ReportOutline
import com.duro.kukie.report.application.port.out.ReportSource
import com.duro.kukie.report.domain.ReportKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

object ReportMarkdown {

    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd")
    private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val MARKUP = Regex("""[\\\[\]<]""")
    private val PULL_REQUEST_URL = Regex("""https://[^\s()<>|]+""")
    private val NUMBERED = Regex("""^\d{1,9}[.)]""")
    private const val BLOCK_MARKERS = "#>-+*=~_`"
    private const val UNKNOWN_USER = "알 수 없는 사용자"
    private const val NONE = "없음"
    private val VERBS = mapOf(
        "apply_manifest" to "apply",
        "scale_resource" to "scale",
        "rollout_restart" to "restart",
        "delete_resource" to "delete",
        "propose_git_change" to "PR",
    )

    private data class Entry(val worklog: WorklogResponse, val turn: WorklogResponse.Turn) {
        val ref = ReportOutline.TurnRef(worklog.conversation.id, turn.seq)
    }

    private data class Row(val entry: Entry, val change: WorklogResponse.Change)

    private class Context(val source: ReportSource, val zone: ZoneId, val names: Map<UUID, String>, entries: List<Entry>) {
        val weekly = source.kind == ReportKind.WEEKLY
        val requesters = entries.mapNotNull { it.turn.requestedBy }.distinct()

        fun name(id: UUID?): String = id?.let { names[it]?.let(::plain) ?: UNKNOWN_USER } ?: "-"

        fun day(at: Instant): String = DAY.format(at.atZone(zone))

        fun short(at: Instant): String = SHORT.format(at.atZone(zone))

        fun full(at: Instant): String = FULL.format(at.atZone(zone))

        /** 주간 보고서는 누가 언제 했는지, 대화 보고서는 여럿이 쓴 대화일 때만 누가 했는지 붙인다. */
        fun who(entries: List<Entry>): String? {
            val people = entries.mapNotNull { it.turn.requestedBy }.distinct().joinToString(", ") { name(it) }
            if (!weekly) return people.takeIf { requesters.size > 1 && it.isNotEmpty() }
            val date = entries.minOfOrNull { it.turn.startedAt }?.let { MONTH_DAY.format(it.atZone(zone)) }

            return listOfNotNull(people.takeIf { it.isNotEmpty() }, date).joinToString(", ").takeIf { it.isNotEmpty() }
        }
    }

    /** outline 이 없으면 한 일에는 변경만 적는다. metrics 는 주간 보고서에만 준다. */
    fun render(title: String, zone: ZoneId, source: ReportSource, outline: ReportOutline?, metrics: ClusterMetricsResult? = null): String {
        val entries = source.conversations.flatMap { worklog ->
            worklog.turns.filter { it.kind == RunKind.CHAT }.map { Entry(worklog, it) }
        }
        val names = source.conversations.flatMap { it.people }.associate { it.id to it.name }
        val context = Context(source, zone, names, entries)
        val rows = entries.flatMap { entry -> entry.turn.changes.map { Row(entry, it) } }
        val work = work(context, outline, entries)

        return buildString {
            appendLine("# ${plain(title)}")
            appendLine()
            appendLine(lead(context, entries, rows))
            outline?.summary?.let(::paragraph)?.takeIf { it.isNotEmpty() }?.let {
                appendLine()
                appendLine("## 요약")
                appendLine()
                appendLine(it)
            }
            metrics?.let { metrics(it) }
            if (work.isEmpty() && rows.isEmpty()) {
                appendLine()
                appendLine("변경이나 기록할 작업 없음.")
                return@buildString
            }
            section(if (context.weekly) "주요 작업" else "한 일", work)
            changes(context, rows)
            section("남은 일", followUps(context, rows, outline))
        }
    }

    private fun lead(context: Context, entries: List<Entry>, rows: List<Row>): String {
        val source = context.source
        val parts = buildList {
            add(paragraph(source.clusterName))
            if (context.weekly) {
                val start = source.periodStart?.let(context::full) ?: "-"
                val end = source.periodEnd?.let(context::full) ?: "-"
                add("$start ~ $end (${context.zone.id})")
                add("대화 ${source.conversations.size}개")
            } else {
                source.conversations.firstOrNull()?.let { add("namespace ${WorklogMarkdown.code(it.environment.namespace)}") }
                days(context, entries)?.let { add("$it (${context.zone.id})") }
                context.requesters.joinToString(", ") { context.name(it) }.takeIf { it.isNotEmpty() }?.let(::add)
            }
            add(count(rows))
        }

        return parts.joinToString(" · ")
    }

    private fun days(context: Context, entries: List<Entry>): String? {
        val first = entries.minOfOrNull { it.turn.startedAt }?.let(context::day) ?: return null
        val last = entries.maxOf { it.turn.finishedAt ?: it.turn.startedAt }.let(context::day)

        return if (first == last) first else "$first ~ $last"
    }

    private fun count(rows: List<Row>): String {
        if (rows.isEmpty()) return "변경 $NONE"
        val results = rows.groupingBy { result(it.change.status) }.eachCount().entries.joinToString(" · ") { "${it.key} ${it.value}" }

        return "변경 ${rows.size}건 ($results)"
    }

    /** AI 가 뺀 변경도 한 줄씩 넣고, 한 턴은 처음 고른 작업 하나에만 둔다. */
    private fun work(context: Context, outline: ReportOutline?, entries: List<Entry>): List<String> {
        val byRef = entries.associateBy { it.ref }
        val covered = mutableSetOf<ReportOutline.TurnRef>()
        val chosen = outline?.tasks.orEmpty().mapNotNull { task ->
            val picked = task.turns.filter(covered::add).mapNotNull(byRef::get)
            if (picked.isEmpty()) return@mapNotNull null
            val summary = task.summary?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()

            "**${plain(task.title)}**$summary${tail(context.who(picked))}"
        }
        val left = entries.filter { it.ref !in covered }.flatMap { entry ->
            entry.turn.changes.map { "${label(it)}${tail(result(it.status), context.who(listOf(entry)))}" }
        }

        return chosen + left
    }

    private fun StringBuilder.metrics(result: ClusterMetricsResult) {
        appendLine()
        appendLine("## 운영 지표")
        appendLine()
        when (result) {
            ClusterMetricsResult.NoPrometheus -> appendLine("클러스터에서 Prometheus 를 찾지 못해 지표를 싣지 못함.")
            ClusterMetricsResult.Unreadable -> appendLine("Prometheus 를 읽지 못함. 권한(services/proxy)이나 연결을 확인해야 함.")
            is ClusterMetricsResult.Available -> metricTable(metricRows(result.metrics))
        }
    }

    private fun StringBuilder.metricTable(rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) {
            appendLine("Prometheus 에 노드·파드 지표가 없음. kube-state-metrics 와 node-exporter 가 있어야 함.")
            return
        }
        appendLine("| 항목 | 기간 중 값 |")
        appendLine("|---|---|")
        rows.forEach { (name, value) -> appendLine("| $name | ${cell(value)} |") }
    }

    private fun metricRows(metrics: ClusterMetrics): List<Pair<String, String>> = buildList {
        val nodes = listOfNotNull(metrics.nodes?.let { "${it}개" }, metrics.nodeReady?.let { "Ready ${uptime(it)}" })
        if (nodes.isNotEmpty()) add("노드" to nodes.joinToString(" · "))
        metrics.apiServerUp?.let { add("API 서버 가동률" to uptime(it)) }
        metrics.workloadsAvailable?.let { add("디플로이먼트 가용률" to uptime(it)) }
        usage(metrics.cpuAverage, metrics.cpuPeak)?.let { add("CPU 사용률" to it) }
        usage(metrics.memoryAverage, metrics.memoryPeak)?.let { add("메모리 사용률" to it) }
        metrics.restarts?.let { total ->
            val top = metrics.topRestarts.joinToString(", ") { "${WorklogMarkdown.code("${oneLine(it.namespace)}/${oneLine(it.pod)}")} ${it.count}회" }
            add("파드 재시작" to if (total > 0 && top.isNotEmpty()) "${total}회 ($top)" else "${total}회")
        }
    }

    /** 99.99% 가 100% 로 보이지 않게 소수 둘째 자리에서 버린다. */
    private fun uptime(ratio: Double): String =
        BigDecimal.valueOf(ratio).movePointRight(2).setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + "%"

    private fun usage(average: Double?, peak: Double?): String? = listOfNotNull(
        average?.let { "평균 ${(it * 100).roundToInt()}%" },
        peak?.let { "최대 ${(it * 100).roundToInt()}%" },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

    private fun tail(vararg parts: String?): String =
        parts.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(" · ", " (", ")").orEmpty()

    private fun StringBuilder.section(title: String, lines: List<String>) {
        appendLine()
        appendLine("## $title")
        appendLine()
        if (lines.isEmpty()) appendLine(NONE) else lines.forEach { appendLine("- $it") }
    }

    private fun StringBuilder.changes(context: Context, rows: List<Row>) {
        if (!context.weekly) {
            appendLine()
            appendLine("## 변경")
            appendLine()
            if (rows.isEmpty()) appendLine(NONE) else table(context, rows)
            return
        }

        appendLine()
        appendLine("## 팀원별 변경")
        if (rows.isEmpty()) {
            appendLine()
            appendLine(NONE)
            return
        }
        rows.groupBy { it.entry.turn.requestedBy }
            .entries
            .sortedWith(compareBy({ it.key == null }, { context.name(it.key) }))
            .forEach { (requester, changes) ->
                appendLine()
                appendLine("### ${if (requester == null) "요청자 기록 없음" else context.name(requester)} · ${changes.size}건")
                appendLine()
                table(context, changes)
            }
    }

    /** 주간 보고서는 사람별로 묶으므로 요청 칸 대신 대화 칸을 둔다. */
    private fun StringBuilder.table(context: Context, rows: List<Row>) {
        val extra = if (context.weekly) "대화" else "요청"
        appendLine("| 시각 | $extra | 대상 | 위험도 | 결정 | 결과 | PR |")
        appendLine("|---|---|---|---|---|---|---|")
        rows.forEach { (entry, change) ->
            val at = change.appliedAt ?: change.decision?.at ?: change.proposedAt ?: entry.turn.startedAt
            val where = if (context.weekly) plain(entry.worklog.conversation.title) else context.name(entry.turn.requestedBy)
            val decision = change.decision?.let { "${context.name(it.by)} ${if (it.approved) "승인" else "거절"}" } ?: "-"
            val cells = listOf(context.short(at), where, subject(change), plain(change.risk), decision, result(change.status), pullRequest(change))

            appendLine(cells.joinToString(" | ", "| ", " |") { cell(it) })
        }
    }

    private fun followUps(context: Context, rows: List<Row>, outline: ReportOutline?): List<String> = buildList {
        rows.forEach { (entry, change) ->
            val state = open(change) ?: return@forEach
            val reason = change.failureReason?.let { " ${WorklogMarkdown.code(it.name)}" }.orEmpty()
            val where = if (context.weekly) tail(plain(entry.worklog.conversation.title)) else ""

            add("$state: ${label(change)}$reason$where")
        }
        outline?.tasks.orEmpty().flatMap { it.followUps }.map(::paragraph).filter { it.isNotEmpty() }.forEach(::add)
    }

    private fun label(change: WorklogResponse.Change): String =
        change.intent?.let(::paragraph)?.takeIf { it.isNotEmpty() } ?: subject(change)

    /** 무엇을 건드렸는지. 계획에 리소스가 없으면 도구 이름만 적는다. */
    private fun subject(change: WorklogResponse.Change): String {
        val target = change.target as? Map<*, *>
        val resources = (target?.get("resources") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().ifEmpty { listOfNotNull(target) }
        val named = resources.mapNotNull { resource ->
            val name = (resource["name"] as? String)?.let(::oneLine)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            listOfNotNull((resource["kind"] as? String)?.let(::oneLine), name).joinToString("/")
        }
        val verb = plain(VERBS[change.tool] ?: change.tool)
        if (named.isEmpty()) return verb

        return "$verb ${WorklogMarkdown.code(named.first())}" + if (named.size > 1) " 외 ${named.size - 1}개" else ""
    }

    private fun result(status: PlanStatus): String = status.resultLabel()

    /** 끝나지 않았거나 사람이 다시 봐야 하는 변경. */
    private fun open(change: WorklogResponse.Change): String? = when (change.status) {
        PlanStatus.FAILED, PlanStatus.UNKNOWN, PlanStatus.WAITING_APPROVAL,
        PlanStatus.DRAFT, PlanStatus.APPROVED, PlanStatus.EXECUTING -> result(change.status)
        else -> null
    }

    private fun pullRequest(change: WorklogResponse.Change): String {
        val pullRequest = change.pullRequest as? Map<*, *> ?: return "-"
        val url = (pullRequest["url"] as? String)?.takeIf(PULL_REQUEST_URL::matches) ?: return "-"

        return "[#${(pullRequest["number"] as? Number)?.toLong() ?: "PR"}]($url)"
    }

    private fun oneLine(text: String): String = text.lines().joinToString(" ") { it.trim() }.trim()

    /** 사람·AI 가 쓴 글이 링크·이미지·HTML 로 읽히지 않게 한다. */
    private fun plain(text: String): String = oneLine(text).replace(MARKUP) { "\\" + it.value }

    /** 줄 머리에 오는 글이 제목·목록·코드 블록으로 읽히지 않게 한다. */
    private fun paragraph(text: String): String {
        val line = plain(text)
        val numbered = NUMBERED.find(line)

        return when {
            numbered != null -> line.replaceRange(numbered.range.last, numbered.range.last + 1, "\\" + line[numbered.range.last])
            line.firstOrNull()?.let { it in BLOCK_MARKERS } == true -> "\\$line"
            else -> line
        }
    }

    private fun cell(text: String): String = text.replace("|", "\\|")
}
