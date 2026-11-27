package com.duro.kukie.report.application

import java.util.UUID

/** 커밋 뒤에 쓰이므로 엔티티 대신 id 만 담는다. */
data class ReportRequested(val reportId: UUID)
