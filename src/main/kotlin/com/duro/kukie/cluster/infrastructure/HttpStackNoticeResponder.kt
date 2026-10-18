package com.duro.kukie.cluster.infrastructure

import com.duro.kukie.cluster.application.port.`in`.StackNoticeCommand
import com.duro.kukie.cluster.application.port.out.StackNoticeResponder
import com.duro.kukie.cluster.application.port.out.StackNoticeResult
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** CloudFormation 이 준 S3 presigned 주소에 결과를 PUT 한다. 서명에 Content-Type 이 없어 그 헤더를 붙이지 않는다. */
@Component
class HttpStackNoticeResponder : StackNoticeResponder {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val jsonMapper = JsonMapper.builder().build()

    override fun accepts(responseUrl: String): Boolean = CloudFormationResponseUrl.matches(responseUrl)

    override fun respond(notice: StackNoticeCommand, result: StackNoticeResult) {
        check(accepts(notice.responseUrl)) { "CloudFormation 응답 주소가 아니다" }
        val body = jsonMapper.writeValueAsString(
            mapOf(
                "Status" to if (result.success) "SUCCESS" else "FAILED",
                "Reason" to result.reason,
                "PhysicalResourceId" to result.physicalResourceId,
                "StackId" to notice.stackId,
                "RequestId" to notice.requestId,
                "LogicalResourceId" to notice.logicalResourceId,
            ),
        )
        val request = HttpRequest.newBuilder(URI(notice.responseUrl))
            .timeout(TIMEOUT)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        check(status in 200..299) { "CloudFormation 회신이 거절됐다: $status" }
    }

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}

/**
 * CloudFormation 사용자 지정 리소스의 응답 주소: https://cloudformation-custom-resource-response-<리전>.s3[.<리전>].amazonaws.com/…
 * 포트·사용자 정보가 붙은 주소는 받지 않는다. 내부망이나 다른 서비스로 부르게 만들 수 없게 S3 의 그 버킷 이름만 받는다.
 */
object CloudFormationResponseUrl {

    private val HOST = Regex("cloudformation-custom-resource-response-[a-z0-9]+\\.s3([.-][a-z0-9-]+)?\\.amazonaws\\.com")

    fun matches(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null && HOST.matches(uri.host.orEmpty())
    }
}
