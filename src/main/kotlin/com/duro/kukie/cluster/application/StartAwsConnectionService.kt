package com.duro.kukie.cluster.application

import com.duro.kukie.cluster.application.port.`in`.StartAwsConnectionCommand
import com.duro.kukie.cluster.domain.AwsConnection
import com.duro.kukie.cluster.domain.AwsConnectionRepository
import com.duro.kukie.cluster.presentation.dto.response.AwsConnectionResponse
import com.duro.kukie.global.config.properties.ClusterAwsProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.net.URLEncoder

@Service
class StartAwsConnectionService(
    private val awsConnectionRepository: AwsConnectionRepository,
    private val clusterPermission: ClusterPermission,
    private val clusterAwsProperties: ClusterAwsProperties,
) {

    operator fun invoke(command: StartAwsConnectionCommand): AwsConnectionResponse {
        clusterPermission.requireTeamAdmin(command.teamId, command.userId)
        val connection = awsConnectionRepository.findByTeamId(command.teamId) ?: create(command)

        return AwsConnectionResponse(
            externalId = connection.externalId,
            quickCreateUrl = quickCreateUrl(connection.externalId),
            templateUrl = clusterAwsProperties.templateUrl,
            stackName = clusterAwsProperties.stackNameFor(connection.externalId),
            accountId = connection.accountId,
        )
    }

    /** 동시에 시작해 팀 unique 에 걸리면 먼저 만든 것을 쓴다. */
    private fun create(command: StartAwsConnectionCommand): AwsConnection =
        try {
            awsConnectionRepository.saveAndFlush(AwsConnection(teamId = command.teamId, createdBy = command.userId))
        } catch (exception: DataIntegrityViolationException) {
            awsConnectionRepository.findByTeamId(command.teamId) ?: throw exception
        }

    private fun quickCreateUrl(externalId: String): String =
        "https://console.aws.amazon.com/cloudformation/home?region=${encode(clusterAwsProperties.consoleRegion)}" +
            "#/stacks/quickcreate?templateURL=${encode(clusterAwsProperties.templateUrl)}" +
            "&stackName=${encode(clusterAwsProperties.stackNameFor(externalId))}" +
            "&param_ExternalId=${encode(externalId)}"

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
