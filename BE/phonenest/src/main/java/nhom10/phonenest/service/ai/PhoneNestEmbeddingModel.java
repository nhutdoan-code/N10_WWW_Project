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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PhoneNestEmbeddingModel implements EmbeddingModel {

    // [THÊM MỚI] Thời gian cache trạng thái Ollama.
    private static final long AVAILABLE_CACHE_NANOS = Duration.ofSeconds(30).toNanos();
    private static final long UNAVAILABLE_CACHE_NANOS = Duration.ofSeconds(5).toNanos();

    private final String baseUrl;
    private final String modelName;
    private final int dimensions;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // [THÊM MỚI] Cache trạng thái kết nối Ollama.
    private volatile boolean cachedAvailable = false;
    private volatile long availabilityValidUntilNanos = 0;

    public PhoneNestEmbeddingModel(
            @Value("${phonenest.ai.embedding.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${phonenest.ai.embedding.ollama.model:bge-m3}") String modelName,
            @Value("${phonenest.ai.embedding.dimensions:1024}") int dimensions) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL không được để trống.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Tên embedding model không được để trống.");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions phải lớn hơn 0.");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        this.dimensions = dimensions;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();

        log.info("Khởi tạo PhoneNestEmbeddingModel: baseUrl={}, model={}, dimensions={}",
                this.baseUrl, this.modelName, this.dimensions);
    }
    /**
     * [THÊM MỚI] Constructor mặc định phục vụ unit test.
     */
    public PhoneNestEmbeddingModel() {
        this("http://127.0.0.1:11434", "bge-m3", 1024);
    }
    /**
     * Tạo embedding từ một đoạn văn bản.
     */
    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Không thể tạo embedding từ nội dung rỗng.");
        }
        try {
            Map<String, Object> requestBody = Map.of("model", modelName, "prompt", text);
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // [CHỈNH SỬA] HTTP lỗi: đánh dấu Ollama không khả dụng.
            if (response.statusCode() != 200) {
                danhDauKhongKhaDung();
                throw new IllegalStateException(
                        "Ollama embedding API trả về HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.get("embedding");

            // [CHỈNH SỬA] Lỗi dữ liệu không đồng nghĩa mất kết nối.
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new IllegalStateException("Ollama không trả về trường 'embedding' hợp lệ.");
            }

            int size = embeddingNode.size();
            if (size != dimensions) {
                throw new IllegalStateException(
                        "Sai kích thước embedding! Mong đợi: " + dimensions + ", thực tế: " + size);
            }

            float[] vector = new float[size];
            for (int i = 0; i < size; i++) {
                JsonNode value = embeddingNode.get(i);
                if (value == null || !value.isNumber()) {
                    throw new IllegalStateException("Embedding chứa giá trị không hợp lệ tại vị trí " + i);
                }
                double number = value.asDouble();
                if (!Double.isFinite(number)) {
                    throw new IllegalStateException("Embedding chứa giá trị không hữu hạn tại vị trí " + i);
                }
                vector[i] = (float) number;
            }
            // [CHỈNH SỬA] Embedding thành công => Ollama đang hoạt động.
            danhDauKhaDung();
            return vector;

        } catch (InterruptedException e) {
            // [THÊM MỚI] Khôi phục trạng thái interrupt của thread.
            Thread.currentThread().interrupt();
            danhDauKhongKhaDung();
            log.warn("Yêu cầu embedding Ollama bị gián đoạn. Model: {}", modelName);
            throw new IllegalStateException("Yêu cầu embedding Ollama bị gián đoạn.", e);

        } catch (HttpTimeoutException e) {
            danhDauKhongKhaDung();
            log.warn("Ollama embedding timeout. Model: {}, lỗi: {}", modelName, e.getMessage());
            throw new IllegalStateException("Ollama embedding bị timeout.", e);

        } catch (IOException e) {
            // Lỗi giao tiếp HTTP hoặc đọc phản hồi.
            danhDauKhongKhaDung();
            log.warn("Không thể giao tiếp với Ollama. Model: {}, lỗi: {}", modelName, e.getMessage());
            throw new IllegalStateException("Không thể giao tiếp với Ollama tại " + baseUrl, e);

        } catch (RuntimeException e) {
            // Không đánh dấu mất kết nối với lỗi dữ liệu hoặc cấu hình.
            log.error("Lỗi xử lý embedding. Model: {}, lỗi: {}", modelName, e.getMessage());
            throw e;
        }
    }
    /**
     * Tạo embedding từ Document.
     */
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
    /**
     * Tạo embedding từ danh sách văn bản.
     * [LƯU Ý] API /api/embeddings hiện được giữ nguyên. Các văn bản được gửi lần lượt đến Ollama.
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }
    /**
     * Xử lý EmbeddingRequest của Spring AI.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (request == null || request.getInstructions() == null) {
            throw new IllegalArgumentException("EmbeddingRequest không hợp lệ.");
        }
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = embed(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }
    /**
     * Kích thước vector embedding.
     */
    @Override
    public int dimensions() {
        return dimensions;
    }
    /**
     * [CHỈNH SỬA] Chỉ gọi Ollama /api/tags khi cache hết hạn.
     */
    public synchronized boolean isAvailable() {
        long now = System.nanoTime();
        if (now < availabilityValidUntilNanos) {
            return cachedAvailable;
        }
        boolean available = kiemTraOllama();
        if (available) {
            danhDauKhaDung();
        } else {
            danhDauKhongKhaDung();
        }
        return available;
    }
    /**
     * Kiểm tra Ollama và model embedding.
     */
    private boolean kiemTraOllama() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
                if (name.equals(modelName) || name.startsWith(modelName + ":")) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Kiểm tra Ollama bị gián đoạn.");
            return false;
        } catch (Exception e) {
            log.warn("Không thể kiểm tra Ollama embedding: {}", e.getMessage());
            return false;
        }
    }
    /**
     * Đánh dấu Ollama khả dụng trong 30 giây.
     */
    private void danhDauKhaDung() {
        cachedAvailable = true;
        availabilityValidUntilNanos = System.nanoTime() + AVAILABLE_CACHE_NANOS;
    }
    /**
     * Đánh dấu Ollama không khả dụng trong 5 giây.
     */
    private void danhDauKhongKhaDung() {
        cachedAvailable = false;
        availabilityValidUntilNanos = System.nanoTime() + UNAVAILABLE_CACHE_NANOS;
    }
    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}