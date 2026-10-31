package nhom10.phonenest.repository;

import nhom10.phonenest.entity.ChiTietDonHang;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang, String> {

    List<ChiTietDonHang> findByDonHangMaDonHang(String maDonHang);

    boolean existsByPhienBanMaPhienBan(String maPhienBan);

    // ==========================================
    // Phase 3 & 3A: Top-Selling and Brand Sales Queries (Strict ngayGiao Semantics)
    // ==========================================

    @Query("SELECT c.phienBan.sanPham.maSanPham, c.phienBan.sanPham.tenMay, SUM(c.soLuong) " +
           "FROM ChiTietDonHang c " +
           "WHERE c.donHang.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "GROUP BY c.phienBan.sanPham.maSanPham, c.phienBan.sanPham.tenMay " +
           "ORDER BY SUM(c.soLuong) DESC, c.phienBan.sanPham.maSanPham ASC")
    List<Object[]> findTopSellingDeliveredAllTime(Pageable pageable);

    @Query("SELECT c.phienBan.sanPham.maSanPham, c.phienBan.sanPham.tenMay, SUM(c.soLuong) " +
           "FROM ChiTietDonHang c " +
           "WHERE c.donHang.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "  AND c.donHang.ngayGiao IS NOT NULL " +
           "  AND (:start IS NULL OR c.donHang.ngayGiao >= :start) " +
           "  AND (:end IS NULL OR c.donHang.ngayGiao < :end) " +
           "GROUP BY c.phienBan.sanPham.maSanPham, c.phienBan.sanPham.tenMay " +
           "ORDER BY SUM(c.soLuong) DESC, c.phienBan.sanPham.maSanPham ASC")
    List<Object[]> findTopSellingDeliveredBetweenDeliveryDates(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query("SELECT c.phienBan.sanPham.loai.maLoai, SUM(c.donGia * c.soLuong), SUM(c.soLuong) " +
           "FROM ChiTietDonHang c " +
           "WHERE c.donHang.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "GROUP BY c.phienBan.sanPham.loai.maLoai")
    List<Object[]> findDeliveredSalesByCategoryAllTime();

    @Query("SELECT c.phienBan.sanPham.loai.maLoai, SUM(c.donGia * c.soLuong), SUM(c.soLuong) " +
           "FROM ChiTietDonHang c " +
           "WHERE c.donHang.trangThai = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO " +
           "  AND c.donHang.ngayGiao IS NOT NULL " +
           "  AND (:start IS NULL OR c.donHang.ngayGiao >= :start) " +
           "  AND (:end IS NULL OR c.donHang.ngayGiao < :end) " +
           "GROUP BY c.phienBan.sanPham.loai.maLoai")
    List<Object[]> findDeliveredSalesByCategoryBetweenDeliveryDates(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Tổng số lượng đã bán theo sản phẩm (không tính đơn đã hủy) - dùng cho sort "bán chạy"
    @Query("SELECT c.phienBan.sanPham.maSanPham, SUM(c.soLuong) " +
            "FROM ChiTietDonHang c " +
            "WHERE c.donHang.trangThai <> nhom10.phonenest.enums.TrangThaiDonHang.DA_HUY " +
            "GROUP BY c.phienBan.sanPham.maSanPham")
    List<Object[]> tongDaBanTheoSanPham();
}

