package com.duro.kukie.cluster.application.port.out

/** 모델이 보내는 SSE 응답을 줄 단위로 읽는다. 대화 내용이 지나가므로 줄을 로그나 예외에 넣지 않는다. */
interface ModelChatStream : AutoCloseable {

    /** 다음 줄. 끝나면 null. 읽다 끊기면 GpuClusterApiFailedException. 읽는 스레드를 인터럽트하면 멈춘다. */
    fun readLine(): String?

    /** 상위 연결을 끊는다. 모델도 생성을 멈춘다. */
    override fun close()
}
