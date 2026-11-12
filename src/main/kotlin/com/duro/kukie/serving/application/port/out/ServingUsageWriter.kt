package com.duro.kukie.serving.application.port.out

import com.duro.kukie.serving.application.AuthenticatedServingKey
import com.duro.kukie.serving.application.ServingRejectionObservation
import com.duro.kukie.serving.application.ServingUsageObservation
import java.util.UUID

interface ServingUsageWriter {
    fun complete(event: ServingUsageObservation)

    /** 폐기·만료 여부와 관계없이 키의 access·cluster·user를 찾는다. 사용자가 없는 access의 키는 빠진다. */
    fun identities(keyIds: Collection<UUID>): Map<UUID, AuthenticatedServingKey>

    /** 끝난 요청을 한 번에 넣는다. 같은 requestId 행이 있으면 건너뛴다. */
    fun insertFinished(events: List<ServingUsageObservation>, deadlineSlackSeconds: Long)

    /** 게이트웨이가 거절한 요청 수를 넣는다. 같은 id 행이 있으면 건너뛴다. */
    fun insertRejections(events: List<ServingRejectionObservation>)
}
