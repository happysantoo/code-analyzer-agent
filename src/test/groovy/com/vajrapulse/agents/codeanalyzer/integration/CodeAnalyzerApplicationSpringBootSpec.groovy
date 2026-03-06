package com.vajrapulse.agents.codeanalyzer.integration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

/**
 * Verifies the full Spring Boot application context loads with an embedded H2 database.
 * Relational schema is applied via Flyway (migration-h2); the vector store is stubbed in tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDbConfig)
class CodeAnalyzerApplicationSpringBootSpec extends Specification {

    def "context loads"() {
        expect:
        true // Full context started: H2, Flyway (relational schema), stubbed CodeEmbeddingRepository
    }
}
