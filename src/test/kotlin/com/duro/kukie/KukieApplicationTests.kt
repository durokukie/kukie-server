package com.duro.kukie

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@Import(TestcontainersConfig::class)
@SpringBootTest
class KukieApplicationTests {

    @Test
    fun contextLoads() {
    }

}
