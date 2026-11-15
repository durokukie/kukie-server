package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingUploadStore
import java.time.Duration

class FakeHostingUploadStore : HostingUploadStore, Resettable {

    /** 올라온 파일의 크기. 시험이 "에이전트가 올렸다"를 이것으로 흉내 낸다. */
    val sizes = mutableMapOf<String, Long>()

    override fun presignPut(key: String, bytes: Long, ttl: Duration): String = "https://uploads.test/$key?bytes=$bytes"

    override fun size(key: String): Long? = sizes[key]

    override fun clear() {
        sizes.clear()
    }
}
