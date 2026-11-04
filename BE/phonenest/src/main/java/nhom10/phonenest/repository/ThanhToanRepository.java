package nhom10.phonenest.repository;

import jakarta.persistence.LockModeType;
import nhom10.phonenest.entity.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThanhToanRepository extends JpaRepository<ThanhToan, String> {
    List<ThanhToan> findByDonHangMaDonHangOrderByNgayTaoDesc(String ma);

    Optional<ThanhToan> findByMaGiaoDich(String maGiaoDich);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ThanhToan t where t.maGiaoDich = :ma")
    Optional<ThanhToan> findByMaGiaoDichForUpdate(@Param("ma") String ma);
}
