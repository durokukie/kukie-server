package com.duro.kukie.cluster.infrastructure

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StackNoticeParserTest {

    @Test
    fun `CloudFormation 요청에서 스택 속성을 읽는다`() {
        // when
        val notice = StackNoticeParser.parse(body(""""ExternalId":"kukie-1","RoleArn":"arn","AccessLevel":"full","GpuNodeControl":"true","GpuClusterPattern":"kukie-*""""))

        // then
        notice.shouldNotBeNull().run {
            requestType shouldBe "Create"
            responseUrl shouldBe "https://example.com"
            externalId shouldBe "kukie-1"
            roleArn shouldBe "arn"
            accessLevel shouldBe "full"
            gpuNodeControl shouldBe "true"
            gpuClusterPattern shouldBe "kukie-*"
        }
    }

    @Test
    fun `v5 이하 스택은 GpuNodeControl 이 없다`() {
        StackNoticeParser.parse(body(""""ExternalId":"kukie-1"""")).shouldNotBeNull().run {
            gpuNodeControl.shouldBeNull()
            gpuClusterPattern.shouldBeNull()
        }
    }

    @Test
    fun `GpuNodeControl 이 문자열이 아니면 없는 값으로 읽는다`() {
        StackNoticeParser.parse(body(""""GpuNodeControl":true""")).shouldNotBeNull().gpuNodeControl.shouldBeNull()
    }

    @Test
    fun `CloudFormation 요청 모양이 아니면 버린다`() {
        StackNoticeParser.parse("not json").shouldBeNull()
        StackNoticeParser.parse("""{"RequestType":"Create"}""").shouldBeNull()
    }

    private fun body(properties: String) =
        """
        {"RequestType":"Create","ResponseURL":"https://example.com","StackId":"stack","RequestId":"r",
        "LogicalResourceId":"KukieNotice","ResourceProperties":{$properties}}
        """.trimIndent()
}
