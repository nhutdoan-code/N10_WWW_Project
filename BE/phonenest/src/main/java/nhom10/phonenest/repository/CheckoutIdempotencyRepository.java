package nhom10.phonenest.repository;

import jakarta.persistence.LockModeType;
import nhom10.phonenest.entity.CheckoutIdempotency;
import nhom10.phonenest.enums.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, Long> {

    Optional<CheckoutIdempotency> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CheckoutIdempotency c WHERE c.userId = :userId AND c.idempotencyKey = :key")
    Optional<CheckoutIdempotency> findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") String userId,
            @Param("key") String key
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheckoutIdempotency c SET c.status = :targetStatus, c.requestHash = :fingerprint, c.updatedAt = :now " +
            "WHERE c.id = :id AND c.status = :expectedStatus AND c.requestHash = :fingerprint")
    int transitionFromFailedToInProgress(
            @Param("id") Long id,
            @Param("fingerprint") String fingerprint,
            @Param("now") LocalDateTime now,
            @Param("targetStatus") IdempotencyStatus targetStatus,
            @Param("expectedStatus") IdempotencyStatus expectedStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheckoutIdempotency c SET c.status = :status, c.orderId = :orderId, c.responsePayload = :payload, c.updatedAt = :now " +
            "WHERE c.id = :id AND c.status = :expectedStatus")
    int markSuccessInCurrentTx(
            @Param("id") Long id,
            @Param("orderId") String orderId,
            @Param("payload") String payload,
            @Param("now") LocalDateTime now,
            @Param("status") IdempotencyStatus status,
            @Param("expectedStatus") IdempotencyStatus expectedStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CheckoutIdempotency c SET c.status = :failedStatus, c.updatedAt = :now " +
            "WHERE c.id = :id AND c.status = :inProgressStatus")
    int markFailedIfInProgress(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("failedStatus") IdempotencyStatus failedStatus,
            @Param("inProgressStatus") IdempotencyStatus inProgressStatus
    );
}
