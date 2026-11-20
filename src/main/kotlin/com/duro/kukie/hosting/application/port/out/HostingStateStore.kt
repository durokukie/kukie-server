package com.duro.kukie.hosting.application.port.out

import java.time.Duration

/** 배포기가 상태 파일을 적는 버킷. 서버는 읽기만 한다. */
interface HostingStateStore {

    /** prefix 아래의 키를 이름순으로 준다. */
    fun list(prefix: String): List<String>

    /** prefix 바로 아래의 폴더(공통 접두어, / 로 끝남)를 이름순으로 준다. 그 아래 파일은 나열하지 않는다. */
    fun listFolders(prefix: String): List<String>

    /** prefix 아래에서 startAfter 보다 뒤이고 until 이하인 키를 이름순으로 준다. until 을 넘으면 더 나열하지 않는다. */
    fun listBetween(prefix: String, startAfter: String, until: String): List<String>

    /** 파일이 없으면 null. */
    fun read(key: String): String?

    /** 파일의 끝 bytes 바이트만. 없으면 null. */
    fun readTail(key: String, bytes: Int): String?

    /** 브라우저가 서버를 거치지 않고 받을 주소. filename 으로 저장된다. */
    fun presignGet(key: String, ttl: Duration, filename: String): String
}
