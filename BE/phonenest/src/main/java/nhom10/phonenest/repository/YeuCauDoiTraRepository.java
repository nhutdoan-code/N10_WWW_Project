package nhom10.phonenest.repository;

import nhom10.phonenest.entity.YeuCauDoiTra;
import nhom10.phonenest.enums.LoaiYeuCauDoiTra;
import nhom10.phonenest.enums.TrangThaiDoiTra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface YeuCauDoiTraRepository extends JpaRepository<YeuCauDoiTra, String>, KhoaYeuCauDoiTraRepository {

    @Query(value = """
            SELECT DISTINCT y FROM YeuCauDoiTra y
              JOIN y.donHang d JOIN d.khachHang kh
            WHERE y.trangThai IN :statuses
              AND (:pattern IS NULL OR LOWER(y.maYeuCau) LIKE :pattern OR LOWER(d.maDonHang) LIKE :pattern
                   OR LOWER(kh.hoTen) LIKE :pattern OR LOWER(d.soDienThoaiNhan) LIKE :pattern
                   OR EXISTS (SELECT detail.maChiTietDoiTra FROM ChiTietDoiTra detail
                       JOIN detail.chiTietDonHang line WHERE detail.yeuCau = y
                       AND (LOWER(line.tenSanPham) LIKE :pattern OR LOWER(line.tenPhienBan) LIKE :pattern)))
              AND (:type IS NULL OR EXISTS (SELECT detail.maChiTietDoiTra FROM ChiTietDoiTra detail
                   WHERE detail.yeuCau = y AND detail.loaiYeuCau = :type))
              AND (:start IS NULL OR y.ngayTao >= :start)
              AND (:end IS NULL OR y.ngayTao < :end)
              AND (:assignedTo IS NULL OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'return' AND a.maYeuCau = y.maYeuCau AND a.maNguoiPhuTrach = :assignedTo))
              AND (:deadlineOverdue = false OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'return' AND a.maYeuCau = y.maYeuCau AND a.hanXuLy < :now))
              AND (:slaOnly = false OR (y.ngayTao <= :slaCutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')))
              AND (:updatedAfter IS NULL OR y.ngayCapNhat >= :updatedAfter)
              AND (:updatedBefore IS NULL OR y.ngayCapNhat < :updatedBefore)
            ORDER BY CASE WHEN :ascending = true THEN y.ngayTao END ASC,
                     CASE WHEN :ascending = false THEN y.ngayTao END DESC, y.maYeuCau DESC
            """, countQuery = """
            SELECT COUNT(DISTINCT y.maYeuCau) FROM YeuCauDoiTra y
              JOIN y.donHang d JOIN d.khachHang kh
            WHERE y.trangThai IN :statuses
              AND (:pattern IS NULL OR LOWER(y.maYeuCau) LIKE :pattern OR LOWER(d.maDonHang) LIKE :pattern
                   OR LOWER(kh.hoTen) LIKE :pattern OR LOWER(d.soDienThoaiNhan) LIKE :pattern
                   OR EXISTS (SELECT detail.maChiTietDoiTra FROM ChiTietDoiTra detail
                       JOIN detail.chiTietDonHang line WHERE detail.yeuCau = y
                       AND (LOWER(line.tenSanPham) LIKE :pattern OR LOWER(line.tenPhienBan) LIKE :pattern)))
              AND (:type IS NULL OR EXISTS (SELECT detail.maChiTietDoiTra FROM ChiTietDoiTra detail
                   WHERE detail.yeuCau = y AND detail.loaiYeuCau = :type))
              AND (:start IS NULL OR y.ngayTao >= :start)
              AND (:end IS NULL OR y.ngayTao < :end)
              AND (:assignedTo IS NULL OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'return' AND a.maYeuCau = y.maYeuCau AND a.maNguoiPhuTrach = :assignedTo))
              AND (:deadlineOverdue = false OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'return' AND a.maYeuCau = y.maYeuCau AND a.hanXuLy < :now))
              AND (:slaOnly = false OR (y.ngayTao <= :slaCutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')))
              AND (:updatedAfter IS NULL OR y.ngayCapNhat >= :updatedAfter)
              AND (:updatedBefore IS NULL OR y.ngayCapNhat < :updatedBefore)
            """)
    Page<YeuCauDoiTra> searchAdmin(
            @Param("statuses") Collection<TrangThaiDoiTra> statuses,
            @Param("pattern") String pattern,
            @Param("type") LoaiYeuCauDoiTra type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("assignedTo") String assignedTo,
            @Param("deadlineOverdue") boolean deadlineOverdue,
            @Param("now") LocalDateTime now,
            @Param("slaOnly") boolean slaOnly,
            @Param("slaCutoff") LocalDateTime slaCutoff,
            @Param("updatedAfter") LocalDateTime updatedAfter,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            @Param("ascending") boolean ascending,
            Pageable pageable);

    long countByTrangThaiIn(Collection<TrangThaiDoiTra> statuses);

    @Query("SELECT COUNT(y) FROM YeuCauDoiTra y WHERE y.trangThai IN :statuses AND y.ngayTao <= :cutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')")
    long countSlaOverdue(@Param("statuses") Collection<TrangThaiDoiTra> statuses, @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(y) FROM YeuCauDoiTra y WHERE y.trangThai IN :statuses AND y.ngayCapNhat >= :start AND y.ngayCapNhat < :end")
    long countUpdatedByStatusAndRange(@Param("statuses") Collection<TrangThaiDoiTra> statuses,
                                      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<YeuCauDoiTra> findByDonHangMaDonHangOrderByNgayTaoDesc(String maDonHang);

    @Query("SELECT COUNT(y) FROM YeuCauDoiTra y WHERE (:start IS NULL OR y.ngayTao >= :start) AND (:end IS NULL OR y.ngayTao < :end)")
    long countByNgayTaoRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(y) FROM YeuCauDoiTra y WHERE y.trangThai IN :statuses AND (:start IS NULL OR y.ngayTao >= :start) AND (:end IS NULL OR y.ngayTao < :end)")
    long countOpenByNgayTaoRange(@Param("statuses") Collection<TrangThaiDoiTra> statuses,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);
}
