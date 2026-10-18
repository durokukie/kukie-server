package com.duro.kukie.cluster.infrastructure

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CloudFormationResponseUrlTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://cloudformation-custom-resource-response-apnortheast2.s3.ap-northeast-2.amazonaws.com/arn/x?X-Amz-Signature=s",
            "https://cloudformation-custom-resource-response-useast1.s3.amazonaws.com/arn/x",
            "https://cloudformation-custom-resource-response-uswest2.s3-us-west-2.amazonaws.com/arn/x",
        ],
    )
    fun `CloudFormation 의 S3 응답 주소는 받는다`(url: String) {
        CloudFormationResponseUrl.matches(url) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://cloudformation-custom-resource-response-useast1.s3.amazonaws.com/x",
            "https://cloudformation-custom-resource-response-useast1.s3.amazonaws.com:8443/x",
            "https://user@cloudformation-custom-resource-response-useast1.s3.amazonaws.com/x",
            "https://cloudformation-custom-resource-response-useast1.s3.amazonaws.com.evil.example/x",
            "https://evil.example/cloudformation-custom-resource-response-useast1.s3.amazonaws.com",
            "https://my-bucket.s3.amazonaws.com/x",
            "http://169.254.169.254/latest/meta-data",
            "not a url",
        ],
    )
    fun `그 밖의 주소는 받지 않는다`(url: String) {
        CloudFormationResponseUrl.matches(url) shouldBe false
    }
}
