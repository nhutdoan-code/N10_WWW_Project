package nhom10.phonenest.repository;

import nhom10.phonenest.entity.LoaiSanPham;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoaiSanPhamRepository extends JpaRepository<LoaiSanPham, String> {

    boolean existsByMaLoaiCha(String maLoaiCha);

    @Query("SELECT l FROM LoaiSanPham l " +
           "WHERE LOWER(l.tenLoai) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(l.maLoai) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "ORDER BY l.tenLoai ASC")
    List<LoaiSanPham> searchAdmin(@Param("pattern") String pattern, Pageable pageable);
}
