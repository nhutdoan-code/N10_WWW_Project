
package nhom10.phonenest.repository;

import jakarta.persistence.LockModeType;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SanPhamRepository extends JpaRepository<SanPham, String> {

    /** Khoa dong san pham (SELECT ... FOR UPDATE) truoc khi sua/luu tru/khoi phuc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SanPham s WHERE s.maSanPham = :ma")
    Optional<SanPham> findByIdForUpdate(@Param("ma") String ma);

    boolean existsByLoaiMaLoai(String maLoai);

    long countByThuongHieuMaThuongHieu(String maThuongHieu);

    @Query("SELECT s FROM SanPham s " +
            "WHERE (:pattern IS NULL OR LOWER(s.tenMay) LIKE :pattern OR LOWER(s.maSanPham) LIKE :pattern) " +
            "ORDER BY s.maSanPham DESC")
    List<SanPham> searchAdmin(@Param("pattern") String pattern, Pageable pageable);

    @Query(value = """
        SELECT s FROM SanPham s
        WHERE (:pattern IS NULL
            OR LOWER(s.tenMay) LIKE LOWER(:pattern)
            OR LOWER(s.maSanPham) LIKE LOWER(:pattern)
            OR LOWER(s.thuongHieu.tenThuongHieu) LIKE LOWER(:pattern)
            OR LOWER(s.loai.tenLoai) LIKE LOWER(:pattern))
          AND (:status IS NULL OR s.trangThai = :status)
        ORDER BY s.maSanPham DESC
        """,
        countQuery = """
        SELECT COUNT(s) FROM SanPham s
        WHERE (:pattern IS NULL
            OR LOWER(s.tenMay) LIKE LOWER(:pattern)
            OR LOWER(s.maSanPham) LIKE LOWER(:pattern)
            OR LOWER(s.thuongHieu.tenThuongHieu) LIKE LOWER(:pattern)
            OR LOWER(s.loai.tenLoai) LIKE LOWER(:pattern))
          AND (:status IS NULL OR s.trangThai = :status)
        """)
    Page<SanPham> searchAdminPage(@Param("pattern") String pattern,
                                  @Param("status") TrangThaiSanPham status,
                                  Pageable pageable);

    @Query("""
        SELECT DISTINCT s
        FROM SanPham s
        LEFT JOIN FETCH s.phienBans
        WHERE s.maSanPham = :ma
    """)
    Optional<SanPham> findByIdWithPhienBans(@Param("ma") String ma);

    /** Toan bo san pham kem danh sach phien ban (dong bo vector store cho chatbot). */
    @Query("""
        SELECT DISTINCT s
        FROM SanPham s
        LEFT JOIN FETCH s.phienBans
    """)
    List<SanPham> findAllWithPhienBans();

    /**
     * Lấy sản phẩm đang kinh doanh và có ít nhất một phiên bản
     * đang kinh doanh, còn hàng.
     *
     * JOIN FETCH giúp lấy phiên bản cùng sản phẩm,
     * tránh truy vấn riêng từng sản phẩm khi đọc tồn kho.
     */
    @Query("""
    SELECT DISTINCT s
    FROM SanPham s
    LEFT JOIN FETCH s.phienBans
    WHERE s.trangThai = :status
      AND EXISTS (
          SELECT p.maPhienBan
          FROM PhienBanSanPham p
          WHERE p.sanPham = s
            AND p.trangThai = :status
            AND p.soLuongTon > 0
      )
    """)
    List<SanPham> findAllWithPhienBansCoHang(
            @Param("status") TrangThaiSanPham status
    );
}
