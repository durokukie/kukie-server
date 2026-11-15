package com.duro.kukie.cluster.domain

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class GpuClusterPatternTest {

    @Test
    fun `이름이 같으면 맞는다`() {
        GpuClusterPattern.matches("kukie-gpu", "kukie-gpu") shouldBe true
    }

    @Test
    fun `별표는 비어 있어도 되는 아무 글자열에 맞는다`() {
        GpuClusterPattern.matches("*", "kukie-gpu") shouldBe true
        GpuClusterPattern.matches("kukie-*", "kukie-gpu") shouldBe true
        GpuClusterPattern.matches("kukie-*", "kukie-") shouldBe true
        GpuClusterPattern.matches("*-gpu", "kukie-gpu") shouldBe true
        GpuClusterPattern.matches("k*-*u", "kukie-gpu") shouldBe true
        GpuClusterPattern.matches("**", "kukie-gpu") shouldBe true
    }

    @Test
    fun `물음표는 글자 하나에만 맞는다`() {
        GpuClusterPattern.matches("gpu-?", "gpu-1") shouldBe true
        GpuClusterPattern.matches("gpu-?", "gpu-") shouldBe false
        GpuClusterPattern.matches("gpu-?", "gpu-12") shouldBe false
    }

    @Test
    fun `되돌아가야 맞는 패턴도 맞춘다`() {
        GpuClusterPattern.matches("*ab", "aab") shouldBe true
        GpuClusterPattern.matches("a*b*c", "axbxbxc") shouldBe true
        GpuClusterPattern.matches("a*b*c", "axbxbx") shouldBe false
    }

    @Test
    fun `패턴 밖 이름은 맞지 않는다`() {
        GpuClusterPattern.matches("kukie-*", "other-gpu") shouldBe false
        GpuClusterPattern.matches("kukie", "kukie-gpu") shouldBe false
        GpuClusterPattern.matches("kukie-gpu", "kukie") shouldBe false
    }

    @Test
    fun `IAM 처럼 대소문자를 가린다`() {
        GpuClusterPattern.matches("Kukie-*", "kukie-gpu") shouldBe false
    }

    @Test
    fun `빈 패턴은 어느 이름에도 맞지 않는다`() {
        GpuClusterPattern.matches("", "kukie-gpu") shouldBe false
        GpuClusterPattern.matches("", "") shouldBe false
    }

    @Test
    fun `GpuNodeControl 이 false 면 패턴이 맞아도 control 이 아니다`() {
        // given
        val connection = AwsConnection(teamId = UUID.randomUUID(), createdBy = UUID.randomUUID())
        connection.connect("123456789012", LocalDateTime.now(), null, AccessLevel.FULL, gpuNodeControl = false, gpuClusterPattern = "*")

        // when & then
        connection.gpuControlOn("kukie-gpu") shouldBe false
    }

    @Test
    fun `정규식 글자는 글자 그대로 본다`() {
        GpuClusterPattern.matches("a.c", "abc") shouldBe false
    }

    @Test
    fun `별표가 많은 패턴도 금방 끝난다`() {
        val pattern = "*a".repeat(50)
        val name = "a".repeat(100) + "b"

        assertTimeoutPreemptively(Duration.ofSeconds(1)) {
            GpuClusterPattern.matches(pattern, name) shouldBe false
        }
    }

    @Test
    fun `스택 알림 값은 템플릿 규칙에 맞을 때만 받는다`() {
        GpuClusterPattern.ofNotice("kukie-*_?1") shouldBe "kukie-*_?1"
        GpuClusterPattern.ofNotice("a".repeat(100)) shouldBe "a".repeat(100)
    }

    @Test
    fun `스택 알림 값이 없거나 비었거나 규칙 밖이면 없는 값이다`() {
        GpuClusterPattern.ofNotice(null).shouldBeNull()
        GpuClusterPattern.ofNotice("").shouldBeNull()
        GpuClusterPattern.ofNotice("a/b").shouldBeNull()
        GpuClusterPattern.ofNotice("\$pattern").shouldBeNull()
        GpuClusterPattern.ofNotice("a".repeat(101)).shouldBeNull()
    }
}
