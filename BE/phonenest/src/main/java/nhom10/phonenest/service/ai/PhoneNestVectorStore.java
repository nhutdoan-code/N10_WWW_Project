package nhom10.phonenest.service.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe In-Memory Vector Store implementation of {@link VectorStore}.
 * <p>
 * Stores documents alongside their dense embedding vectors and executes
 * cosine similarity search, threshold filtering, and ranking.
 */
public class PhoneNestVectorStore implements VectorStore {

    private final EmbeddingModel embeddingModel;
    private final Map<String, VectorEntry> store = new ConcurrentHashMap<>();

    public PhoneNestVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null) return;
        for (Document doc : documents) {
            float[] vec = embeddingModel.embed(doc);
            store.put(doc.getId(), new VectorEntry(doc, vec));
        }
    }

    @Override
    public void delete(List<String> idList) {
        if (idList == null) return;
        idList.forEach(store::remove);
    }

    @Override
    public void delete(Filter.Expression expression) {
        // Expression deletion not required for in-memory catalogue store
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return List.of();
        }
        float[] queryVec = embeddingModel.embed(request.getQuery());
        List<Document> results = new ArrayList<>();

        for (VectorEntry entry : store.values()) {
            double sim = cosineSimilarity(queryVec, entry.vector);
            if (sim >= request.getSimilarityThreshold()) {
                Document docWithScore = entry.document.mutate().score(sim).build();
                results.add(docWithScore);
            }
        }

        results.sort(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        int topK = request.getTopK() > 0 ? request.getTopK() : 10;
        return results.stream().limit(topK).toList();
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.builder().query(query).topK(10).build());
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 1e-9 || normB <= 1e-9) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record VectorEntry(Document document, float[] vector) {}
}
