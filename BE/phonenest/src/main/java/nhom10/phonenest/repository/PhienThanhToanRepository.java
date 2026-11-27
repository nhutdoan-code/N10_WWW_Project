package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhienThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhienThanhToanRepository
        extends JpaRepository<PhienThanhToan, String>, KhoaPhienThanhToanRepository {
    Optional<PhienThanhToan> findByKhachHangMaKhAndIdempotencyKey(String maKh, String idempotencyKey);
}
