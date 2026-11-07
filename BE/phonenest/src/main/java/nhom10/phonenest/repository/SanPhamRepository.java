package nhom10.phonenest.repository;

import nhom10.phonenest.entity.SanPham;
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
}
