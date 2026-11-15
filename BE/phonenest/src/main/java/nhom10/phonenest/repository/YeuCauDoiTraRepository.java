package nhom10.phonenest.repository;

import nhom10.phonenest.entity.YeuCauDoiTra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface YeuCauDoiTraRepository extends JpaRepository<YeuCauDoiTra, String>, KhoaYeuCauDoiTraRepository {

    List<YeuCauDoiTra> findByDonHangMaDonHangOrderByNgayTaoDesc(String maDonHang);

    // 2B.7E: findByIdForUpdate nam o KhoaYeuCauDoiTraRepository (khoa + nap lai trang thai moi nhat).

    @Query("SELECT COUNT(y) FROM YeuCauDoiTra y " +
           "WHERE (:start IS NULL OR y.ngayTao >= :start) " +
           "  AND (:end IS NULL OR y.ngayTao < :end)")
    long countByNgayTaoRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
