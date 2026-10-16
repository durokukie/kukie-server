package com.duro.kukie.global.util

import java.time.LocalDateTime
import java.time.ZoneOffset

fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
