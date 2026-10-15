package com.duro.kukie.actionplan.domain

import java.math.BigDecimal
import java.math.BigInteger

object JsonValues {

    fun same(left: Any?, right: Any?): Boolean = when {
        left is Number && right is Number -> left.toBigDecimal().compareTo(right.toBigDecimal()) == 0
        left is Map<*, *> && right is Map<*, *> -> left.keys == right.keys && left.keys.all { same(left[it], right[it]) }
        left is List<*> && right is List<*> -> left.size == right.size && left.indices.all { same(left[it], right[it]) }
        else -> left == right
    }

    private fun Number.toBigDecimal(): BigDecimal = when (this) {
        is BigDecimal -> this
        is BigInteger -> BigDecimal(this)
        is Double, is Float -> BigDecimal(toString())
        else -> BigDecimal.valueOf(toLong())
    }
}
