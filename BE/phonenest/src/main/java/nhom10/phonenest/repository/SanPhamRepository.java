package nhom10.phonenest.repository;

import nhom10.phonenest.entity.SanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SanPhamRepository extends JpaRepository<SanPham, String> {

    boolean existsByLoaiMaLoai(String maLoai);

    @Query("""
        SELECT DISTINCT s
        FROM SanPham s
        LEFT JOIN FETCH s.phienBans
        WHERE s.maSanPham = :ma
    """)
    Optional<SanPham> findByIdWithPhienBans(@Param("ma") String ma);
}