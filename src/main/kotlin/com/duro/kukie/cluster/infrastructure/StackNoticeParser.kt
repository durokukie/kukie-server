package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** 보관함에서 꺼낸 CloudFormation 요청을 읽는다. */
internal object StackNoticeParser {

    private val jsonMapper = JsonMapper.builder().build()

    /** CloudFormation 요청 모양이 아니면 null — 버린다. */
    fun parse(body: String): StackNoticeCommand? {
        val root = runCatching { jsonMapper.readTree(body) }.getOrNull() ?: return null
        val properties = root.path("ResourceProperties")
        return StackNoticeCommand(
            requestType = root.text("RequestType") ?: return null,
            responseUrl = root.text("ResponseURL") ?: return null,
            stackId = root.text("StackId") ?: return null,
            requestId = root.text("RequestId") ?: return null,
            logicalResourceId = root.text("LogicalResourceId") ?: return null,
            physicalResourceId = root.text("PhysicalResourceId"),
            externalId = properties.text("ExternalId"),
            roleArn = properties.text("RoleArn"),
            accessLevel = properties.text("AccessLevel"),
            gpuNodeControl = properties.text("GpuNodeControl"),
        )
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isString }?.stringValue()
}
