package com.duro.kukie.cluster.application

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StackConsoleUrlTest {

    @Test
    fun `스택 ARN 이 있으면 그 리전의 스택 정보 화면을 연다`() {
        StackConsoleUrl.of(STACK_ID, STACK_NAME, "ap-northeast-2") shouldBe
            "https://us-east-1.console.aws.amazon.com/cloudformation/home?region=us-east-1#/stacks/stackinfo" +
            "?stackId=arn%3Aaws%3Acloudformation%3Aus-east-1%3A123456789012%3Astack%2Fkukie-access-kukie-0123456789abcdef" +
            "%2F1a2b3c4d-0000-1111-2222-333344445555"
    }

    @Test
    fun `스택 ARN 이 없으면 알림 리전의 스택 목록을 이름으로 거른다`() {
        StackConsoleUrl.of(null, STACK_NAME, "ap-northeast-2") shouldBe
            "https://ap-northeast-2.console.aws.amazon.com/cloudformation/home?region=ap-northeast-2#/stacks" +
            "?filteringText=$STACK_NAME&filteringStatus=active&viewNested=true"
    }

    @Test
    fun `스택 ARN 모양이 아니면 목록 화면으로 간다`() {
        StackConsoleUrl.of("arn:aws:cloudformation:evil.example/x:123456789012:stack/a/b", STACK_NAME, "ap-northeast-2") shouldBe
            StackConsoleUrl.of(null, STACK_NAME, "ap-northeast-2")
    }

    @Test
    fun `알림의 스택 ARN 은 확인한 계정의 것일 때만 받는다`() {
        StackConsoleUrl.stackIdOf(STACK_ID, "123456789012") shouldBe STACK_ID
        StackConsoleUrl.stackIdOf(STACK_ID, "210987654321").shouldBeNull()
        StackConsoleUrl.stackIdOf("not-an-arn", "123456789012").shouldBeNull()
        StackConsoleUrl.stackIdOf(null, "123456789012").shouldBeNull()
    }

    companion object {
        private const val STACK_NAME = "kukie-access-kukie-0123456789abcdef"
        private const val STACK_ID =
            "arn:aws:cloudformation:us-east-1:123456789012:stack/$STACK_NAME/1a2b3c4d-0000-1111-2222-333344445555"
    }
}
