package com.duro.kukie.hosting.application.port.out

import java.time.Duration

/** 에이전트가 올리는 폴더(상태 버킷의 uploads/). 서버는 올릴 주소를 서명하고 올렸는지만 본다. */
interface HostingUploadStore {

    /** bytes 와 다른 크기는 S3 가 거부하도록 Content-Length 를 서명한다. */
    fun presignPut(key: String, bytes: Long, ttl: Duration): String

    /** 올라온 파일의 크기. 없으면 null. */
    fun size(key: String): Long?
}
