package com.duro.kukie.cluster.application

object ServedModelName {

    /** ServedModel CRD 의 spec.source.huggingface 와 같은 규칙이다. */
    const val MODEL_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$"
    private val MODEL = Regex(MODEL_PATTERN)

    // 오퍼레이터가 "<이름>-download-<8자>" Job 을 만든다. Job 이름이 63자를 넘으면 만들지 못한다
    private const val MAX_LENGTH = 45
    private val NOT_ALLOWED = Regex("[^a-z0-9]+")
    private val KUBERNETES_NAME = Regex("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$")

    /** org/name 의 name 을 쿠버네티스 이름으로 줄인다. 오퍼레이터가 같은 이름의 Service 도 만들어 숫자로 시작할 수 없다. */
    fun of(model: String): String {
        val name = model.substringAfter('/').lowercase().replace(NOT_ALLOWED, "-").trim('-')
        val prefixed = if (name.first().isDigit()) "m-$name" else name
        return prefixed.take(MAX_LENGTH).trimEnd('-')
    }

    fun valid(name: String): Boolean = KUBERNETES_NAME.matches(name)

    fun validModel(model: String): Boolean = MODEL.matches(model)
}
