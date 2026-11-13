package com.duro.kukie.hosting.infrastructure

import com.duro.kukie.global.config.properties.AwsProperties
import com.duro.kukie.global.config.properties.HostingProperties
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.time.Duration

class S3HostingUploadStoreTest {

    @BeforeEach
    fun credentials() {
        System.setProperty("aws.accessKeyId", "AKIATESTTESTTESTTEST")
        System.setProperty("aws.secretAccessKey", "test-secret")
    }

    @AfterEach
    fun clear() {
        System.clearProperty("aws.accessKeyId")
        System.clearProperty("aws.secretAccessKey")
    }

    @Test
    fun `올릴 주소는 크기와 형식을 서명해 다른 크기의 파일은 S3 가 거부한다`() {
        // given
        val store = S3HostingUploadStore(HostingProperties(stateBucket = "test-bucket", commandsQueueUrl = "q"), AwsProperties("ap-northeast-2"))

        // when
        val url = URLDecoder.decode(store.presignPut("uploads/recklehs/shop/abc.tar.gz", 1234, Duration.ofMinutes(10)), Charsets.UTF_8)

        // then
        url shouldContain "test-bucket"
        url shouldContain "uploads/recklehs/shop/abc.tar.gz"
        url shouldContain "X-Amz-SignedHeaders=content-length;content-type;host"
    }
}
