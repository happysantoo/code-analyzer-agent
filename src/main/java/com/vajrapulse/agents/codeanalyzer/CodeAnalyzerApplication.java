package com.vajrapulse.agents.codeanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        // We use our own JdbcCodeEmbeddingRepository; disable Spring AI's pgvector vector-store auto-config.
        "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
public class CodeAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAnalyzerApplication.class, args);
    }

}
