package com.duro.kukie.cluster.application

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ServedModelNameTest {

    @Test
    fun `레포 이름만 남기고 소문자로 줄인다`() {
        ServedModelName.of("openai/gpt-oss-20b") shouldBe "gpt-oss-20b"
        ServedModelName.of("Qwen/Qwen3.6-35B-A3B") shouldBe "qwen3-6-35b-a3b"
    }

    @Test
    fun `이어진 기호는 하이픈 하나로 줄이고 끝의 기호는 뗀다`() {
        ServedModelName.of("org/Model__v1..0_") shouldBe "model-v1-0"
    }

    @Test
    fun `숫자로 시작하면 Service 이름이 될 수 있게 앞에 글자를 붙인다`() {
        ServedModelName.of("org/3b-chat") shouldBe "m-3b-chat"
    }

    @Test
    fun `오퍼레이터의 Job 이름이 63자를 넘지 않게 자르고 끝의 하이픈을 뗀다`() {
        val name = ServedModelName.of("org/" + "a".repeat(44) + "-tail")

        name shouldBe "a".repeat(44)
        ("$name-download-12345678").length shouldBe 62
    }

    @Test
    fun `쿠버네티스 이름인지 본다`() {
        ServedModelName.valid("gpt-oss-20b") shouldBe true
        ServedModelName.valid("a".repeat(63)) shouldBe true
        ServedModelName.valid("a".repeat(64)) shouldBe false
        ServedModelName.valid("Upper") shouldBe false
        ServedModelName.valid("-front") shouldBe false
        ServedModelName.valid("back-") shouldBe false
        ServedModelName.valid("a/b") shouldBe false
        ServedModelName.valid("..") shouldBe false
    }
}
