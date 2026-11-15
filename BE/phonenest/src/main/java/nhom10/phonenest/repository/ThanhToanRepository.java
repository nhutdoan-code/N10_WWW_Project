package nhom10.phonenest.repository;

import jakarta.persistence.LockModeType;
import nhom10.phonenest.entity.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThanhToanRepository extends JpaRepository<ThanhToan, String>, KhoaThanhToanRepository {
    List<ThanhToan> findByDonHangMaDonHangOrderByNgayTaoDesc(String ma);

    Optional<ThanhToan> findByMaGiaoDich(String maGiaoDich);

    // 2B.7E: findByMaGiaoDichForUpdate nam o KhoaThanhToanRepository (khoa + nap lai). Ban @Lock cu tra ve
    // ban ghi da nap truoc do (paymentRef) voi trang thai cu -> IPN that bai den sau co the ghi de DA_THANH_TOAN.
}
