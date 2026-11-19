package nhom10.phonenest.config;

import nhom10.phonenest.service.ai.PhoneNestEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vector store san pham luu trong MariaDB (bang phonenest_vector_store, migration 21).
 * <p>
 * initializeSchema(false): bang do migration tao, backend khong chay DDL (tai khoan DB chi co
 * SELECT/INSERT/UPDATE/DELETE). Tat bang {@code phonenest.ai.vector-store.mariadb.enabled=false}
 * thi SemanticSearchService dung vector store trong bo nho.
 */
@Configuration
@ConditionalOnProperty(name = "phonenest.ai.vector-store.mariadb.enabled", havingValue = "true", matchIfMissing = true)
public class VectorStoreConfig {

    @Bean("phoneNestMariaDbVectorStore")
    public VectorStore phoneNestMariaDbVectorStore(JdbcTemplate jdbcTemplate, PhoneNestEmbeddingModel embeddingModel) {
        return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(embeddingModel.dimensions())
                .distanceType(MariaDBDistanceType.COSINE)
                .vectorTableName("phonenest_vector_store")
                .initializeSchema(false)
                .removeExistingVectorStoreTable(false)
                .build();
    }
}
