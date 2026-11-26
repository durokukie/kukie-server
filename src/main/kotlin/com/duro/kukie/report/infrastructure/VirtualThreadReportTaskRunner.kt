package com.duro.kukie.report.infrastructure

import com.duro.kukie.report.application.port.out.ReportTaskRunner
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

@Component
class VirtualThreadReportTaskRunner : ReportTaskRunner {

    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    // 팀들의 회차가 같은 분에 몰려도 AI 를 한꺼번에 부르지 않는다
    private val slots = Semaphore(MAX_CONCURRENT, true)

    override fun run(task: () -> Unit) {
        executor.execute {
            slots.acquire()
            try {
                task()
            } finally {
                slots.release()
            }
        }
    }

    companion object {
        private const val MAX_CONCURRENT = 4
    }
}
