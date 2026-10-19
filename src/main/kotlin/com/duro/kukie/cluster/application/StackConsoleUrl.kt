package com.duro.kukie.cluster.application

import java.net.URLEncoder

/**
 * 고객이 KukieAccess 스택을 지우러 갈 CloudFormation 콘솔 주소. Kukie 는 고객 스택을 지울 권한이 없어(역할이 자기 자신을
 * 지우게 된다) 연결 해제 때 이 주소를 건넨다.
 */
object StackConsoleUrl {

    private val STACK_ARN = Regex("arn:aws:cloudformation:([a-z0-9-]+):(\\d{12}):stack/([\\w-]+)/[\\w-]+")

    /** 스택 ARN 이 있으면 그 스택의 정보 화면, 없으면(이 칸이 생기기 전에 연결) 알림 리전의 스택 목록을 이름으로 거른 화면. */
    fun of(stackId: String?, stackName: String, fallbackRegion: String): String {
        val region = stackId?.let(STACK_ARN::matchEntire)?.groupValues?.get(1)
            ?: return "${home(fallbackRegion)}#/stacks?filteringText=${encode(stackName)}" +
                "&filteringStatus=active&viewNested=true"
        return "${home(region)}#/stacks/stackinfo?stackId=${encode(stackId)}"
    }

    /** 알림이 말한 스택 ARN 이 그 계정의 CloudFormation 스택일 때만 그대로. 아니면 null. */
    fun stackIdOf(stackId: String?, accountId: String): String? =
        stackId?.takeIf { STACK_ARN.matchEntire(it)?.groupValues?.get(2) == accountId }

    private fun home(region: String): String =
        "https://$region.console.aws.amazon.com/cloudformation/home?region=$region"

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
