package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhienThanhToan;

import java.util.Optional;

public interface KhoaPhienThanhToanRepository {
    Optional<PhienThanhToan> findByMaPhienThanhToanForUpdate(String maPhienThanhToan);
}
