package com.duro.kukie.global.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "report")
data class ReportProperties(
    val outlineModel: String = "gpt-6-luna",
    val claudeModel: String = "claude-sonnet-5-5",
    /** 두 제공사가 같은 이름(low·medium·high)을 받는다. */
    val outlineEffort: String = "medium",
)
