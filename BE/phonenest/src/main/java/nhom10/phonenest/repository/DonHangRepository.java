package nhom10.phonenest.repository;

import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.enums.TrangThaiDonHang;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface DonHangRepository extends JpaRepository<DonHang, String>  , JpaSpecificationExecutor<DonHang> {

    List<DonHang> findByKhachHangMaKhOrderByNgayMuaDesc(String maKh);

    List<DonHang> findAllByOrderByNgayMuaDesc();
    // Khóa dòng đơn hàng khi đổi trạng thái để hai request đồng thời không cùng thành công
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DonHang d where d.maDonHang = :ma")
    Optional<DonHang> findByIdForUpdate(@Param("ma") String ma);

    boolean existsByKhachHangMaKh(String maKh);

    boolean existsByKhachHangMaKhAndTrangThaiNot(String maKh, TrangThaiDonHang t);

    @Query("select count(d)>0 from DonHang d join d.chiTiet c where c.phienBan.sanPham.maSanPham=:ma")
    boolean daCoTrongDonHang(@Param("ma") String ma);

    // ==========================================
    // Phase 3: Optimized Dashboard & Search Queries
    // ==========================================
    // Phase 3 & 3A: Dashboard Aggregations (Strict ngayGiao Semantics)
    // ==========================================

    @Query("SELECT COALESCE(SUM(d.tongThanhToan), 0) FROM DonHang d " +
           "WHERE d.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO")
    BigDecimal sumDeliveredRevenueAllTime();

    @Query("SELECT COALESCE(SUM(d.tongThanhToan), 0) FROM DonHang d " +
           "WHERE d.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "  AND d.ngayGiao IS NOT NULL " +
           "  AND (:start IS NULL OR d.ngayGiao >= :start) " +
           "  AND (:end IS NULL OR d.ngayGiao < :end)")
    BigDecimal sumDeliveredRevenueBetweenDeliveryDates(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(d) FROM DonHang d " +
           "WHERE d.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO")
    long countDeliveredOrdersAllTime();

    @Query("SELECT COUNT(d) FROM DonHang d " +
           "WHERE d.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "  AND d.ngayGiao IS NOT NULL " +
           "  AND (:start IS NULL OR d.ngayGiao >= :start) " +
           "  AND (:end IS NULL OR d.ngayGiao < :end)")
    long countDeliveredOrdersBetweenDeliveryDates(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(d.tongThanhToan), 0) FROM DonHang d " +
           "WHERE d.trangThai IN (:statuses) " +
           "  AND (:start IS NULL OR d.ngayMua >= :start) " +
           "  AND (:end IS NULL OR d.ngayMua < :end)")
    BigDecimal sumPipelineGmv(
            @Param("statuses") Collection<TrangThaiDonHang> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(d) FROM DonHang d " +
           "WHERE d.trangThai IN (:statuses) " +
           "  AND (:start IS NULL OR d.ngayMua >= :start) " +
           "  AND (:end IS NULL OR d.ngayMua < :end)")
    long countPipelineOrders(
            @Param("statuses") Collection<TrangThaiDonHang> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT d.trangThai, COUNT(d) FROM DonHang d " +
           "WHERE (:start IS NULL OR d.ngayMua >= :start) " +
           "  AND (:end IS NULL OR d.ngayMua < :end) " +
           "GROUP BY d.trangThai")
    List<Object[]> countOrdersByStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', d.ngayGiao), SUM(d.tongThanhToan), COUNT(d) FROM DonHang d " +
           "WHERE d.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "  AND d.ngayGiao IS NOT NULL " +
           "  AND (:start IS NULL OR d.ngayGiao >= :start) " +
           "  AND (:end IS NULL OR d.ngayGiao < :end) " +
           "GROUP BY FUNCTION('DATE', d.ngayGiao) " +
           "ORDER BY FUNCTION('DATE', d.ngayGiao) ASC")
    List<Object[]> getDailyRevenueAndDeliveredCount(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT d FROM DonHang d LEFT JOIN d.khachHang kh " +
           "WHERE LOWER(d.maDonHang) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(kh.maKh) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(kh.hoTen) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(d.tenNguoiNhan) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "ORDER BY d.ngayMua DESC")
    List<DonHang> searchAdmin(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT d FROM DonHang d " +
           "WHERE (:start IS NULL OR d.ngayMua >= :start) " +
           "  AND (:end IS NULL OR d.ngayMua < :end) " +
           "ORDER BY d.ngayMua DESC")
    List<DonHang> findCreatedInRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
