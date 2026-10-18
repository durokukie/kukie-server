package com.duro.kukie.cluster.domain

import com.duro.kukie.cluster.exception.ClusterNameTakenException
import com.duro.kukie.cluster.exception.ClusterNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ClusterRepository : JpaRepository<Cluster, UUID> {

    /** ClusterPermission.requireReadable 과 규칙이 같아야 한다. */
    @Query(
        """
        select c from Cluster c
        where c.teamId in (select m.teamId from TeamMembership m where m.userId = :userId)
        order by c.createdAt
        """,
    )
    fun findAllVisibleTo(@Param("userId") userId: UUID): List<Cluster>

    fun findAllByTeamIdOrderByCreatedAt(teamId: UUID): List<Cluster>

    fun existsByTeamIdAndName(teamId: UUID, name: String): Boolean

    @Query(value = "select id from tbl_cluster where team_id = :teamId order by id for update", nativeQuery = true)
    fun lockIdsByTeamId(@Param("teamId") teamId: UUID): List<UUID>

    fun deleteAllByTeamId(teamId: UUID)
}

fun ClusterRepository.findByIdOrThrow(id: UUID): Cluster =
    findByIdOrNull(id) ?: throw ClusterNotFoundException()

fun ClusterRepository.saveAndFlushOrThrowNameTaken(cluster: Cluster): Cluster =
    try {
        saveAndFlush(cluster)
    } catch (exception: DataIntegrityViolationException) {
        if (NAME_UNIQUE_CONSTRAINT in exception.mostSpecificCause.message.orEmpty()) throw ClusterNameTakenException()
        throw exception
    }

private const val NAME_UNIQUE_CONSTRAINT = "uk_cluster_team_name"
