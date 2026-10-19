package com.duro.kukie.cluster.domain

import com.duro.kukie.cluster.exception.AwsConnectionNotStartedException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

interface AwsConnectionRepository : JpaRepository<AwsConnection, UUID> {

    fun findByTeamId(teamId: UUID): AwsConnection?

    fun findByExternalId(externalId: String): AwsConnection?

    fun deleteAllByTeamId(teamId: UUID)

    /**
     * 해제한 스택이 지워졌다고 적는다. 읽은 뒤 AWS 를 부르는 사이에 다시 연결됐으면(계정이 채워졌거나 last 계정이 바뀜)
     * 아무것도 바꾸지 않는다 — 엔티티를 통째로 save 하면 그 연결을 덮어쓴다.
     */
    @Transactional
    @Modifying
    @Query(
        """
        update AwsConnection c set c.lastStackDeletedAt = :at
        where c.id = :id and c.accountId is null and c.lastAccountId = :lastAccountId and c.lastStackDeletedAt is null
        """,
    )
    fun markLastStackDeleted(
        @Param("id") id: UUID,
        @Param("lastAccountId") lastAccountId: String,
        @Param("at") at: LocalDateTime,
    ): Int
}

fun AwsConnectionRepository.findByTeamIdOrThrow(teamId: UUID): AwsConnection =
    findByTeamId(teamId) ?: throw AwsConnectionNotStartedException()
