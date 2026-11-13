package com.duro.kukie.support

import com.duro.kukie.hosting.application.port.out.HostingStateStore
import com.duro.kukie.hosting.exception.HostingUnavailableException
import java.util.TreeMap

/** 버킷 대신 메모리에 파일을 둔다. 키는 S3 처럼 이름순으로 나열한다. */
class FakeHostingStateStore : HostingStateStore, Resettable {

    val files = TreeMap<String, String>()
    var shouldFail = false

    override fun list(prefix: String): List<String> = files.keys.filter { it.startsWith(prefix) }

    override fun read(key: String): String? {
        if (shouldFail) throw HostingUnavailableException()
        return files[key]
    }

    override fun readTail(key: String, bytes: Int): String? = read(key)?.toByteArray()?.takeLast(bytes)?.toByteArray()?.decodeToString()

    override fun clear() {
        files.clear()
        shouldFail = false
    }
}
