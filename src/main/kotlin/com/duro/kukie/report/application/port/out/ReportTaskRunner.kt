package com.duro.kukie.report.application.port.out

fun interface ReportTaskRunner {
    fun run(task: () -> Unit)
}
