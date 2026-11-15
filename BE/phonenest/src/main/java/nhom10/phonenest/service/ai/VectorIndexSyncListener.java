package nhom10.phonenest.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.event.BienTheThayDoiEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 2B.7D: sau khi phe duyet / thu hoi / khoa / mo khoa da COMMIT, tai lap tai lieu vector cua san pham
 * de ngu canh chatbot chi phan anh gia duoc cong bo hien tai. Giao dich rollback -> khong lam gi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorIndexSyncListener {

    private final SemanticSearchService semanticSearchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBienTheThayDoi(BienTheThayDoiEvent e) {
        try {
            semanticSearchService.indexProductById(e.maSanPham());
        } catch (Exception ex) {
            log.warn("Khong the dong bo vector store cho {}: {}", e.maSanPham(), ex.getMessage());
        }
    }
}
