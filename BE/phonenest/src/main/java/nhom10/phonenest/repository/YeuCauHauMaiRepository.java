package nhom10.phonenest.repository;

import nhom10.phonenest.entity.YeuCauHauMai;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiHauMai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YeuCauHauMaiRepository extends JpaRepository<YeuCauHauMai, String> {
    @Query("select y from YeuCauHauMai y left join y.donHang d left join d.khachHang k left join y.khachHang own where own.maKh = :maKh or (own.maKh is null and k.maKh = :maKh) order by y.ngayTao desc")
    List<YeuCauHauMai> findByDonHangKhachHangMaKhOrderByNgayTaoDesc(@Param("maKh") String maKh);

    List<YeuCauHauMai> findAllByOrderByNgayTaoDesc();

    @Query(value = """
            SELECT y FROM YeuCauHauMai y
              LEFT JOIN y.khachHang own LEFT JOIN y.donHang d LEFT JOIN d.khachHang orderOwner
              LEFT JOIN y.chiTietDonHang line
            WHERE y.trangThai IN :statuses
              AND (:pattern IS NULL OR LOWER(y.maYeuCau) LIKE :pattern OR LOWER(d.maDonHang) LIKE :pattern
                   OR LOWER(COALESCE(own.hoTen, orderOwner.hoTen)) LIKE :pattern
                   OR LOWER(d.soDienThoaiNhan) LIKE :pattern
                   OR EXISTS (SELECT account.maTk FROM TaiKhoan account
                       WHERE account.maTk = COALESCE(own.maKh, orderOwner.maKh)
                       AND LOWER(account.soDienThoai) LIKE :pattern)
                   OR LOWER(line.tenSanPham) LIKE :pattern OR LOWER(line.tenPhienBan) LIKE :pattern)
              AND (:type IS NULL OR y.loai = :type)
              AND (:start IS NULL OR y.ngayTao >= :start)
              AND (:end IS NULL OR y.ngayTao < :end)
              AND (:assignedTo IS NULL OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'after' AND a.maYeuCau = y.maYeuCau AND a.maNguoiPhuTrach = :assignedTo))
              AND (:deadlineOverdue = false OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'after' AND a.maYeuCau = y.maYeuCau AND a.hanXuLy < :now))
              AND (:slaOnly = false OR (y.ngayTao <= :slaCutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')))
              AND (:updatedAfter IS NULL OR y.ngayCapNhat >= :updatedAfter)
              AND (:updatedBefore IS NULL OR y.ngayCapNhat < :updatedBefore)
            ORDER BY CASE WHEN :ascending = true THEN y.ngayTao END ASC,
                     CASE WHEN :ascending = false THEN y.ngayTao END DESC, y.maYeuCau DESC
            """, countQuery = """
            SELECT COUNT(DISTINCT y.maYeuCau) FROM YeuCauHauMai y
              LEFT JOIN y.khachHang own LEFT JOIN y.donHang d LEFT JOIN d.khachHang orderOwner
              LEFT JOIN y.chiTietDonHang line
            WHERE y.trangThai IN :statuses
              AND (:pattern IS NULL OR LOWER(y.maYeuCau) LIKE :pattern OR LOWER(d.maDonHang) LIKE :pattern
                   OR LOWER(COALESCE(own.hoTen, orderOwner.hoTen)) LIKE :pattern
                   OR LOWER(d.soDienThoaiNhan) LIKE :pattern
                   OR EXISTS (SELECT account.maTk FROM TaiKhoan account
                       WHERE account.maTk = COALESCE(own.maKh, orderOwner.maKh)
                       AND LOWER(account.soDienThoai) LIKE :pattern)
                   OR LOWER(line.tenSanPham) LIKE :pattern OR LOWER(line.tenPhienBan) LIKE :pattern)
              AND (:type IS NULL OR y.loai = :type)
              AND (:start IS NULL OR y.ngayTao >= :start)
              AND (:end IS NULL OR y.ngayTao < :end)
              AND (:assignedTo IS NULL OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'after' AND a.maYeuCau = y.maYeuCau AND a.maNguoiPhuTrach = :assignedTo))
              AND (:deadlineOverdue = false OR EXISTS (SELECT a.maPhanCong FROM PhanCongHauMai a
                   WHERE a.nguon = 'after' AND a.maYeuCau = y.maYeuCau AND a.hanXuLy < :now))
              AND (:slaOnly = false OR (y.ngayTao <= :slaCutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')))
              AND (:updatedAfter IS NULL OR y.ngayCapNhat >= :updatedAfter)
              AND (:updatedBefore IS NULL OR y.ngayCapNhat < :updatedBefore)
            """)
    Page<YeuCauHauMai> searchAdmin(
            @Param("statuses") Collection<TrangThaiHauMai> statuses,
            @Param("pattern") String pattern,
            @Param("type") LoaiHauMai type,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("assignedTo") String assignedTo,
            @Param("deadlineOverdue") boolean deadlineOverdue,
            @Param("now") java.time.LocalDateTime now,
            @Param("slaOnly") boolean slaOnly,
            @Param("slaCutoff") java.time.LocalDateTime slaCutoff,
            @Param("updatedAfter") java.time.LocalDateTime updatedAfter,
            @Param("updatedBefore") java.time.LocalDateTime updatedBefore,
            @Param("ascending") boolean ascending,
            Pageable pageable);

    long countByTrangThaiIn(Collection<TrangThaiHauMai> statuses);

    @Query("SELECT COUNT(y) FROM YeuCauHauMai y WHERE y.trangThai IN :statuses AND y.ngayTao <= :cutoff AND (y.ghiChuXuLy IS NULL OR TRIM(y.ghiChuXuLy) = '')")
    long countSlaOverdue(@Param("statuses") Collection<TrangThaiHauMai> statuses,
                         @Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("SELECT COUNT(y) FROM YeuCauHauMai y WHERE y.trangThai IN :statuses AND y.ngayCapNhat >= :start AND y.ngayCapNhat < :end")
    long countUpdatedByStatusAndRange(@Param("statuses") Collection<TrangThaiHauMai> statuses,
                                      @Param("start") java.time.LocalDateTime start,
                                      @Param("end") java.time.LocalDateTime end);

    boolean existsByDonHangMaDonHangAndLoaiAndTrangThaiIn(
            String maDonHang, LoaiHauMai loai, Collection<TrangThaiHauMai> trangThai);

    boolean existsByDonHangMaDonHangAndChiTietDonHangMaChiTietAndLoaiAndTrangThaiIn(
            String maDonHang, String maChiTiet, LoaiHauMai loai,
            Collection<TrangThaiHauMai> trangThai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select y from YeuCauHauMai y where y.maYeuCau = :ma")
    Optional<YeuCauHauMai> findByIdForUpdate(@Param("ma") String ma);
}
