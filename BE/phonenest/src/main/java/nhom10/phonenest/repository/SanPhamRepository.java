package nhom10.phonenest.repository;

import nhom10.phonenest.entity.SanPham;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, String> {

    boolean existsByLoaiMaLoai(String maLoai);

    @Query("SELECT s FROM SanPham s " +
           "WHERE LOWER(s.tenMay) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(s.maSanPham) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "ORDER BY s.maSanPham DESC")
    List<SanPham> searchAdmin(@Param("pattern") String pattern, Pageable pageable);
}
