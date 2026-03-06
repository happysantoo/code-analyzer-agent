package com.vajrapulse.agents.codeanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        // Spring AI pgvector autoconfig references JdbcTemplateAutoConfiguration, which is not
        // on the classpath in Spring Boot 4's modular setup; we use our own JdbcCodeEmbeddingRepository.
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
public class CodeAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAnalyzerApplication.class, args);
    }

}
