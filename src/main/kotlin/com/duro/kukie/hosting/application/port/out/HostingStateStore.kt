package com.duro.kukie.hosting.application.port.out

/** 배포기가 상태 파일을 적는 버킷. 서버는 읽기만 한다. */
interface HostingStateStore {

    /** prefix 아래의 키를 이름순으로 준다. */
    fun list(prefix: String): List<String>

    /** 파일이 없으면 null. */
    fun read(key: String): String?
}
