package com.duro.kukie.global.config.properties

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/** 고객 AWS 계정의 KukieAccess 역할로 EKS 를 연결하는 설정. 역할 이름 규칙은 kukie-infra 의 템플릿과 같아야 한다. */
@Validated
@ConfigurationProperties(prefix = "cluster.aws")
data class ClusterAwsProperties(
    @field:NotBlank val templateUrl: String =
        "https://kukie-templates-320343233643-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/kukie-access/v2.yaml",
    @field:NotBlank val stackName: String = "kukie-access",
    @field:NotBlank val consoleRegion: String = "ap-northeast-2",
    @field:NotEmpty val regions: List<String> = listOf("ap-northeast-2"),
    @field:NotBlank val roleNamePrefix: String = "KukieAccess-",
    @field:Min(900) @field:Max(43200) val sessionSeconds: Int = 900,
    /** 템플릿 v2 스택 알림 보관함(SQS) 주소. 없으면 알림을 읽지 않는다(로컬·테스트). */
    val noticeQueueUrl: String? = null,
)
