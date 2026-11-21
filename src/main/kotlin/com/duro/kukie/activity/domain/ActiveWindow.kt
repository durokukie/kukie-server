package com.duro.kukie.activity.domain

/** 활동 사용자를 세는 창. label 은 지표 라벨 window 의 값이다. */
enum class ActiveWindow(val label: String) {
    FIVE_MINUTES("5m"),
    ONE_HOUR("1h"),
    ONE_DAY("24h"),
}
