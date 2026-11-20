package com.duro.kukie.hosting.domain

import java.time.LocalDate
import java.time.ZoneId

/** 호스팅 서비스의 종료일. */
object HostingTerms {

    /** 이날 서비스가 끝나고 앱과 데이터가 지워진다. 신청 화면이 동의를 받기 전에 보여 준다. */
    val CLOSES_ON: LocalDate = LocalDate.of(2026, 12, 12)

    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
}
