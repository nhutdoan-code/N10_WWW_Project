package nhom10.phonenest.repository;

import nhom10.phonenest.entity.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThanhToanRepository extends JpaRepository<ThanhToan, String> {
    List<ThanhToan> findByDonHangMaDonHangOrderByNgayTaoDesc(String ma);

    Optional<ThanhToan> findByMaGiaoDich(String maGiaoDich);
}
