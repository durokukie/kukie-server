package com.duro.kukie.cluster.domain

import com.duro.kukie.cluster.exception.AwsConnectionNotStartedException
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AwsConnectionRepository : JpaRepository<AwsConnection, UUID> {

    fun findByTeamId(teamId: UUID): AwsConnection?

    fun findByExternalId(externalId: String): AwsConnection?

    fun deleteAllByTeamId(teamId: UUID)
}

fun AwsConnectionRepository.findByTeamIdOrThrow(teamId: UUID): AwsConnection =
    findByTeamId(teamId) ?: throw AwsConnectionNotStartedException()
