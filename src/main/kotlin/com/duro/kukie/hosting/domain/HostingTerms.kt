package com.duro.kukie.hosting.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** 호스팅 서비스의 가입 조건과 종료일. */
object HostingTerms {

    /** 이날 서비스가 끝나고 앱과 데이터가 지워진다. 신청 화면이 동의를 받기 전에 보여 준다. */
    val CLOSES_ON: LocalDate = LocalDate.of(2026, 12, 13)

    /** GitHub 계정을 만든 지 이만큼 지나야 신청할 수 있다. 방금 만든 계정으로 여러 번 가입하는 것을 막는다. */
    val MIN_ACCOUNT_AGE: Duration = Duration.ofDays(30)

    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
}
