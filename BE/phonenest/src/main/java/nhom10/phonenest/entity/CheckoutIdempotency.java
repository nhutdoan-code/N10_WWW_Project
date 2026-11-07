package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.IdempotencyStatus;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "checkout_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_idempotency", columnNames = {"user_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "idx_idempotency_order", columnList = "order_id"),
        @Index(name = "idx_idempotency_created", columnList = "created_at")
    }
)
@Getter
@Setter
public class CheckoutIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "order_id", length = 50)
    private String orderId;

    @Lob
    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
