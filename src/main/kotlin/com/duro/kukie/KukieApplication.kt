package com.duro.kukie

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KukieApplication

fun main(args: Array<String>) {
    runApplication<KukieApplication>(*args)
}
