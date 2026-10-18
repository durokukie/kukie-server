package com.duro.kukie.cluster.infrastructure

import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity
import java.time.Clock
import java.time.Duration
import java.util.Base64

/** `aws eks get-token` 과 같은 토큰: x-k8s-aws-id 를 서명에 넣은 STS GetCallerIdentity presigned URL. */
class EksTokenSigner(
    private val clock: Clock = Clock.systemUTC(),
) {

    private val signer = AwsV4HttpSigner.create()

    fun sign(credentials: AwsCredentialsIdentity, region: String, clusterName: String): String {
        val request = SdkHttpRequest.builder()
            .method(SdkHttpMethod.GET)
            .protocol("https")
            .host("sts.$region.amazonaws.com")
            .encodedPath("/")
            .putRawQueryParameter("Action", "GetCallerIdentity")
            .putRawQueryParameter("Version", "2011-06-15")
            .putHeader(CLUSTER_ID_HEADER, clusterName)
            .build()

        val signed = signer.sign {
            it.identity(credentials)
                .request(request)
                .putProperty(AwsV4HttpSigner.REGION_NAME, region)
                .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, SERVICE)
                .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
                .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, PRESIGN_EXPIRATION)
                .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, true)
                .putProperty(HttpSigner.SIGNING_CLOCK, clock)
        }

        val url = signed.request().getUri().toString()
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val TOKEN_PREFIX = "k8s-aws-v1."
        const val CLUSTER_ID_HEADER = "x-k8s-aws-id"
        private const val SERVICE = "sts"
        private val PRESIGN_EXPIRATION: Duration = Duration.ofSeconds(60)
    }
}
