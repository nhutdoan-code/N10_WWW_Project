package nhom10.phonenest.config;

import nhom10.phonenest.service.ai.PhoneNestEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, PhoneNestEmbeddingModel embeddingModel) {
        return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(MariaDBDistanceType.COSINE)
                .vectorTableName("phonenest_vector_store")
                .initializeSchema(true)
                .removeExistingVectorStoreTable(false)
                .build();
    }
}