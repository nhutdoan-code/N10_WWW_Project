package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.repository.DonHangRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.orders.auto-complete-enabled", havingValue = "true", matchIfMissing = true)
public class OrderAutoCompletionScheduler {
    private final DonHangRepository donHangRepository;
    private final DonHangService donHangService;

    // Không giữ transaction chung: lỗi một đơn không làm rollback các đơn khác.
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        var ids = donHangRepository.findAutoCompletionCandidates(TrangThaiDonHang.CHO_KHACH_XAC_NHAN,
                LocalDateTime.now().minusHours(DonHangService.AUTO_COMPLETE_HOURS));
        for (String id : ids) {
            try {
                if (donHangService.tuDongHoanThanh(id)) {
                    log.info("Auto-completed order {}", id);
                }
            } catch (RuntimeException ex) {
                log.warn("Auto-completion failed for order {}; will retry if still eligible", id, ex);
            }
        }
    }
}
