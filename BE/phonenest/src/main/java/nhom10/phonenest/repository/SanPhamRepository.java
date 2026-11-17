
package nhom10.phonenest.repository;

import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SanPhamRepository extends JpaRepository<SanPham, String> {

    boolean existsByLoaiMaLoai(String maLoai);

    long countByThuongHieuMaThuongHieu(String maThuongHieu);

    @Query("SELECT s FROM SanPham s " +
            "WHERE (:pattern IS NULL OR LOWER(s.tenMay) LIKE :pattern OR LOWER(s.maSanPham) LIKE :pattern) " +
            "ORDER BY s.maSanPham DESC")
    List<SanPham> searchAdmin(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
        SELECT DISTINCT s
        FROM SanPham s
        LEFT JOIN FETCH s.phienBans
        WHERE s.maSanPham = :ma
    """)
    Optional<SanPham> findByIdWithPhienBans(@Param("ma") String ma);

    // [THÊM MỚI] Lấy toàn bộ sản phẩm kèm danh sách phiên bản
    @Query("""
        SELECT DISTINCT s
        FROM SanPham s
        LEFT JOIN FETCH s.phienBans
    """)
    List<SanPham> findAllWithPhienBans();// [THÊM MỚI] Bước 1: Tìm mã sản phẩm theo tên hoặc mã, giới hạn số lượng
    @Query("""
    SELECT s.maSanPham
    FROM SanPham s
    WHERE s.trangThai = :status
      AND (
          LOWER(s.tenMay) LIKE :pattern
          OR LOWER(s.maSanPham) LIKE :pattern
      )
    ORDER BY s.maSanPham DESC
""")
    List<String> searchChatbotIds(
            @Param("pattern") String pattern,
            @Param("status") TrangThaiSanPham status,
            Pageable pageable
    );

    // [THÊM MỚI] Bước 2: Lấy thông tin sản phẩm, phiên bản, loại và thương hiệu
    @Query("""
    SELECT DISTINCT s
    FROM SanPham s
    LEFT JOIN FETCH s.loai
    LEFT JOIN FETCH s.thuongHieu
    LEFT JOIN FETCH s.phienBans
    WHERE s.maSanPham IN :ids
""")
    List<SanPham> findChatbotProductsWithVariants(
            @Param("ids") List<String> ids
    );

    // [THÊM MỚI] Bước 3: Lấy hình ảnh riêng, tránh fetch hai collection List cùng lúc
    @Query("""
    SELECT DISTINCT s
    FROM SanPham s
    LEFT JOIN FETCH s.hinhAnhs
    WHERE s.maSanPham IN :ids
""")
    List<SanPham> findChatbotProductsWithImages(
            @Param("ids") List<String> ids
    );

}
