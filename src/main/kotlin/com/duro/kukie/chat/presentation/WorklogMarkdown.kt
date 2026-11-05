package com.duro.kukie.chat.presentation

import com.duro.kukie.chat.domain.RunKind
import com.duro.kukie.chat.presentation.dto.response.WorklogResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object WorklogMarkdown {

    private val KST = ZoneId.of("Asia/Seoul")
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val TITLE_LIMIT = 60

    fun render(worklog: WorklogResponse): String {
        val names = worklog.people.associate { it.id to it.name }
        val conversation = worklog.conversation
        val environment = worklog.environment

        return buildString {
            appendLine("# 작업내역 · ${conversation.title.lines().joinToString(" ")}")
            appendLine()
            appendLine("- 대화: ${if (conversation.shared) "공유(Shared)" else "개인(Private)"} · 만든 사람 ${name(names, conversation.ownerId)}")
            appendLine("- 클러스터: ${environment.clusterName} · context ${code(environment.context)} · namespace ${code(environment.namespace)}")
            appendLine("- 턴 ${worklog.turns.size}개 · 변경 ${worklog.turns.sumOf { it.changes.size }}개")
            worklog.turns.forEach { turn(it, names) }
        }
    }

    private fun StringBuilder.turn(turn: WorklogResponse.Turn, names: Map<UUID, String>) {
        appendLine()
        appendLine("---")
        appendLine()
        if (turn.kind == RunKind.MODE_CHANGE) {
            appendLine("## ${turn.seq}. 모드 변경 → ${turn.mode}")
            return
        }
        appendLine("## ${turn.seq}. ${headline(turn.request)}")
        appendLine()
        val by = turn.requestedBy?.let { name(names, it) } ?: "보낸 사람 기록 없음"
        appendLine("요청: $by · ${time(turn.startedAt)} · 상태 ${code(turn.status.value)}")
        turn.request?.takeIf { it.isNotBlank() }?.let { request ->
            appendLine()
            request.lines().forEach { appendLine("> $it") }
        }

        if (turn.steps.isNotEmpty()) {
            appendLine()
            appendLine("### 실행한 명령")
            turn.steps.forEach { step(it) }
        }
        if (turn.changes.isNotEmpty()) {
            appendLine()
            appendLine("### 변경")
            turn.changes.forEach { change(it, names) }
        }
        turn.answer?.let { answer(it) }
        turn.error?.let {
            appendLine()
            appendLine("### 오류")
            appendLine()
            appendLine(listOfNotNull(it.code?.let(::code), it.message).joinToString(" "))
        }
    }

    private fun StringBuilder.step(step: WorklogResponse.Step) {
        appendLine()
        val access = when (step.access) {
            "read-only" -> "읽기 전용"
            "mutating" -> "변경"
            else -> "구분 없음"
        }
        appendLine("**${step.label ?: "명령"}** · $access")
        appendLine()
        fenced(listOfNotNull(step.command?.let { "$ $it" }, step.output).joinToString("\n"))
    }

    private fun StringBuilder.change(change: WorklogResponse.Change, names: Map<UUID, String>) {
        appendLine()
        appendLine("#### ${change.intent?.takeIf { it.isNotBlank() }?.lines()?.joinToString(" ") { it.trim() } ?: change.tool}")
        appendLine()
        appendLine("- 도구 ${code(change.tool)} · 위험도 ${code(change.risk)} · 상태 ${code(change.status.name)}")
        val command = change.command.joinToString(" ") { shellWord(it.toString()) }
        if (command.isNotEmpty() && '\n' !in command) appendLine("- 명령: ${code(command)}")
        change.dryRun?.get("status")?.let { appendLine("- 예행(dry-run): $it") }
        change.decision?.let {
            val who = it.by?.let { id -> name(names, id) } ?: "알 수 없음"
            appendLine("- 결정: ${if (it.approved) "승인" else "거절"} · $who${it.at?.let { at -> " · ${time(at)}" }.orEmpty()}")
        }
        change.result?.let { result ->
            val exit = result["exit_code"]?.let { "exit $it" } ?: "exit 없음"
            val merge = (result["merge_commit"] as? String)?.let { " · merge ${code(it)}" }.orEmpty()
            appendLine("- 결과: ${if (result["success"] == true) "성공" else "실패"} · $exit$merge")
        }
        ((change.pullRequest as? Map<*, *>)?.get("url") as? String)?.let { appendLine("- PR: $it") }
        change.failureReason?.let { appendLine("- 실패 이유: ${code(it.name)}") }
        if ('\n' in command) {
            appendLine()
            fenced(command)
        }
        val output = listOfNotNull(change.result?.get("stdout") as? String, change.result?.get("stderr") as? String)
            .filter { it.isNotBlank() }
        if (output.isNotEmpty()) {
            appendLine()
            fenced(output.joinToString("\n"))
        }
    }

    private fun StringBuilder.answer(answer: WorklogResponse.Answer) {
        appendLine()
        appendLine("### 답")
        answer.narration?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }
        answer.finding?.let {
            appendLine()
            it.cause?.let { cause -> appendLine("- 원인: $cause") }
            it.evidence?.let { evidence -> appendLine("- 근거: $evidence") }
            it.recommendation?.let { recommendation -> appendLine("- 권장 조치: $recommendation") }
        }
        answer.suggestedNextAction?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("다음에 할 일: $it")
        }
    }

    private fun StringBuilder.fenced(text: String) {
        val fence = "`".repeat(maxOf(3, longestBacktickRun(text) + 1))
        appendLine(fence)
        appendLine(text)
        appendLine(fence)
    }

    private fun code(text: String): String {
        val ticks = "`".repeat(longestBacktickRun(text) + 1)
        val pad = if (text.startsWith("`") || text.endsWith("`")) " " else ""
        return "$ticks$pad$text$pad$ticks"
    }

    private fun longestBacktickRun(text: String): Int =
        Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0

    private fun shellWord(word: String): String =
        if (word.isNotEmpty() && word.all { it.isLetterOrDigit() || it in SAFE_CHARS }) word else "'" + word.replace("'", "'\\''") + "'"

    private fun headline(request: String?): String {
        val line = request?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: return "(요청 없음)"
        return if (line.length <= TITLE_LIMIT) line else line.take(TITLE_LIMIT) + "…"
    }

    private fun name(names: Map<UUID, String>, id: UUID): String = names[id] ?: "알 수 없는 사용자"

    private fun time(utc: LocalDateTime): String = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).format(TIME) + " KST"

    private fun time(iso: String): String =
        runCatching { Instant.parse(iso).atZone(KST).format(TIME) + " KST" }.getOrDefault(iso)

    private const val SAFE_CHARS = "-_./=:,@%+"
}
