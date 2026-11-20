package com.duro.kukie.hosting.presentation.dto.response

import com.duro.kukie.hosting.domain.HostingBuild
import com.duro.kukie.hosting.domain.HostingFailure
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class HostingAgentLoginResponse(
    /** 허용 화면에 보이는 코드. 사용자는 화면의 코드가 이것과 같은지 본다. */
    val code: String,
    /** 허용 화면 주소({WEB_APP_URL}/?go=agent&code=<code>). 사용자는 열고 허용만 누른다. */
    val loginUrl: String,
    /** 에이전트가 토큰을 받을 때 쓰는 값. 사용자에게 보여 주지 않는다. */
    val pollToken: String,
    val expiresIn: Long,
)

data class HostingAgentLoginRequestResponse(
    val code: String,
    /** 에이전트가 로그인을 시작한 시각. 지금 요청한 것이 아니면 남에게 받은 코드일 수 있다. */
    val requestedAt: Instant,
)

data class HostingAgentPendingResponse(val status: String = "pending")

data class HostingAgentDeployResponse(
    /** 이 배포의 id. 시작·결과 대기에 쓴다. */
    val deployId: String,
    val app: String,
    /** 같은 이름의 업로드 앱이 이미 있다. 사용자가 그 앱을 고치라고 한 게 아니면 덮어쓸지 먼저 묻는다. */
    val existing: Boolean,
    val lastDeployedAt: Instant?,
    val upload: HostingAgentUploadResponse,
)

data class HostingAgentUploadResponse(
    /** PUT 으로 올린다. 크기가 bytes 와 다르면 S3 가 거부한다. */
    val url: String,
    val contentType: String,
    val expiresIn: Long,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HostingAgentDeployStatusResponse(
    val done: Boolean,
    /** queued, building, deploying, retrying(시작하지 못해 AI 가 고른 명령으로 다시 배포하는 중), succeeded, failed */
    val status: String,
    val elapsed: Long,
    val url: String? = null,
    val build: HostingBuild? = null,
    /** failure.code 는 배포기의 실패 코드와 거절 이유, 그리고 stuck(오래 움직이지 않음). */
    val failure: HostingFailure? = null,
    /** 사용자에게 그대로 전해도 되는 한국어 안내. 실패했을 때와, 성공했지만 배포기가 시작 명령을 고쳤을 때 있다. */
    val hint: String? = null,
    /** 빌드 로그 끝 8000자. */
    val logTail: String? = null,
    /** 성공한 업로드 앱에만: 레포 연결 안내 한 줄. */
    val next: String? = null,
)
