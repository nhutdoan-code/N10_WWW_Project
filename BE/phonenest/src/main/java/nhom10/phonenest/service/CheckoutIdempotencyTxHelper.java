package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.entity.CheckoutIdempotency;
import nhom10.phonenest.enums.IdempotencyStatus;
import nhom10.phonenest.repository.CheckoutIdempotencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CheckoutIdempotencyTxHelper {

    private final CheckoutIdempotencyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckoutIdempotency saveNewInProgress(String userId, String key, String fingerprint) {
        CheckoutIdempotency record = new CheckoutIdempotency();
        record.setUserId(userId);
        record.setIdempotencyKey(key);
        record.setRequestHash(fingerprint);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transitionFromFailedToInProgress(Long id, String fingerprint) {
        int updated = repository.transitionFromFailedToInProgress(
                id,
                fingerprint,
                LocalDateTime.now(),
                IdempotencyStatus.IN_PROGRESS,
                IdempotencyStatus.FAILED
        );
        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(Long id) {
        int updated = repository.markFailedIfInProgress(
                id,
                LocalDateTime.now(),
                IdempotencyStatus.FAILED,
                IdempotencyStatus.IN_PROGRESS
        );
        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long id, String orderId, String jsonPayload) {
        repository.markSuccessInCurrentTx(
                id,
                orderId,
                jsonPayload,
                LocalDateTime.now(),
                IdempotencyStatus.SUCCESS,
                IdempotencyStatus.IN_PROGRESS
        );
    }
}
