package nhom10.phonenest.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Production Pretrained Embedding Model connecting to Ollama Local Embedding Engine.
 * <p>
 * Uses a genuine pretrained multilingual transformer embedding model (e.g., BAAI BGE-M3 or Nomic-Embed-Text)
 * to transform arbitrary Vietnamese natural language queries and factual product specifications
 * into dense semantic vectors.
 * <p>
 * Architectural invariants:
 * - NO handcrafted concept subspaces or developer-maintained synonym dictionaries.
 * - NO character n-gram hashing masquerading as AI.
 * - Semantic relationships are purely learned from the pretrained weights of the underlying neural model.
 */
@Slf4j
@Component
public class PhoneNestEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final String modelName;
    private final int dimensions;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PhoneNestEmbeddingModel(
            @Value("${phonenest.ai.embedding.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${phonenest.ai.embedding.ollama.model:bge-m3}") String modelName,
            @Value("${phonenest.ai.embedding.dimensions:1024}") int dimensions) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("Khởi tạo PhoneNestEmbeddingModel với Ollama: baseUrl={}, model={}, dimensions={}",
                this.baseUrl, this.modelName, this.dimensions);
    }

//    public PhoneNestEmbeddingModel() {
//        this("http://127.0.0.1:11434", "bge-m3", 1024);
//    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Không thể tạo embedding từ nội dung rỗng.");
        }
        try {
            Map<String, Object> reqBody = Map.of(
                    "model", modelName,
                    "prompt", text
            );
            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama embedding API trả về HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embNode = root.get("embedding");
            if (embNode == null || !embNode.isArray()) {
                throw new IllegalStateException("Ollama không trả về trường 'embedding': " + response.body());
            }

            int size = embNode.size();
            float[] vec = new float[size];
            if (vec.length != dimensions) {
                throw new IllegalStateException(
                        "Sai kích thước embedding! Mong đợi: "
                                + dimensions + ", thực tế: " + vec.length
                );
            }
            for (int i = 0; i < size; i++) {
                vec[i] = (float) embNode.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            log.error("Lỗi khi tạo embedding từ Ollama. Model: {}, lỗi: {}", modelName, e.getMessage());
            throw new RuntimeException("Ollama embedding không khả dụng tại " + baseUrl + " (model: " + modelName + "): " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document không được null.");
        }
        String text = document.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Nội dung document không được rỗng.");
        }
        return embed(text);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null) return List.of();
        List<float[]> result = new ArrayList<>();
        for (String t : texts) {
            result.add(embed(t));
        }
        return result;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            float[] vec = embed(instructions.get(i));
            embeddings.add(new Embedding(vec, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return false;
            }
            for (JsonNode model : models) {
                String name = model.path("name").asText("");
                // Chấp nhận cả bge-m3 và bge-m3:latest
                if (name.equals(modelName)
                        || name.startsWith(modelName + ":")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Không thể kiểm tra Ollama embedding: {}", e.getMessage());
            return false;
        }
    }

    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
