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

public interface DonHangRepository extends JpaRepository<DonHang, String>, JpaSpecificationExecutor<DonHang>, KhoaDonHangRepository {

    List<DonHang> findByKhachHangMaKhOrderByNgayMuaDesc(String maKh);

    List<DonHang> findAllByOrderByNgayMuaDesc();

    @Query("SELECT DISTINCT d FROM DonHang d WHERE EXISTS (" +
           "SELECT t FROM ThanhToan t WHERE t.donHang = d AND (" +
           "t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.COD OR " +
           "(t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.CHUYEN_KHOAN " +
           "AND t.trangThai IN (nhom10.phonenest.enums.TrangThaiThanhToan.DA_THANH_TOAN, " +
           "nhom10.phonenest.enums.TrangThaiThanhToan.CAN_HOAN_TIEN)) OR " +
           "(t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.VI_DIEN_TU " +
           "AND t.trangThai IN (nhom10.phonenest.enums.TrangThaiThanhToan.DA_THANH_TOAN, " +
           "nhom10.phonenest.enums.TrangThaiThanhToan.CAN_HOAN_TIEN)))) " +
           "ORDER BY d.ngayMua DESC")
    List<DonHang> findAllVisibleToAdmin();
    // Khóa dòng đơn hàng khi đổi trạng thái để hai request đồng thời không cùng thành công
    // 2B.7D: findByIdForUpdate nam o KhoaDonHangRepository (khoa + nap lai trang thai moi nhat).

    boolean existsByKhachHangMaKh(String maKh);

    boolean existsByKhachHangMaKhAndTrangThaiNot(String maKh, TrangThaiDonHang t);

    @Query("select count(d)>0 from DonHang d join d.chiTiet c where c.phienBan.sanPham.maSanPham=:ma")
    boolean daCoTrongDonHang(@Param("ma") String ma);

    @Query("select count(distinct d) from DonHang d join d.chiTiet c where c.phienBan.sanPham.maSanPham = :ma and d.trangThai not in (nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO, nhom10.phonenest.enums.TrangThaiDonHang.DA_HUY)")
    long countActiveOrdersBySanPham(@Param("ma") String ma);

    @Query("select count(distinct d) from DonHang d join d.chiTiet c where c.phienBan.sanPham.maSanPham = :ma")
    long countAllOrdersBySanPham(@Param("ma") String ma);

    @Query("select count(d) from DonHang d where d.khuyenMai.maKm = :ma or d.khuyenMaiVanChuyen.maKm = :ma")
    long countOrdersByKhuyenMai(@Param("ma") String ma);

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

    @Query("SELECT DISTINCT d FROM DonHang d LEFT JOIN d.khachHang kh " +
           "WHERE (LOWER(d.maDonHang) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(kh.maKh) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(kh.hoTen) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(d.tenNguoiNhan) LIKE LOWER(:pattern) ESCAPE '\\') " +
           "AND EXISTS (SELECT t FROM ThanhToan t WHERE t.donHang = d AND (" +
           "t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.COD OR " +
           "(t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.CHUYEN_KHOAN " +
           "AND t.trangThai IN (nhom10.phonenest.enums.TrangThaiThanhToan.DA_THANH_TOAN, " +
           "nhom10.phonenest.enums.TrangThaiThanhToan.CAN_HOAN_TIEN)) OR " +
           "(t.phuongThuc = nhom10.phonenest.enums.PhuongThucTt.VI_DIEN_TU " +
           "AND t.trangThai IN (nhom10.phonenest.enums.TrangThaiThanhToan.DA_THANH_TOAN, " +
           "nhom10.phonenest.enums.TrangThaiThanhToan.CAN_HOAN_TIEN)))) " +
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
