package nhom11.phonenest.repository;

import nhom11.phonenest.entity.SanPham;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, String> {

    @Query("""
            select s from SanPham s
            where (lower(s.tenMay) like lower(concat('%', :q, '%'))
                   or lower(s.maSanPham) like lower(concat('%', :q, '%'))
                   or lower(s.loai.tenLoai) like lower(concat('%', :q, '%')))
              and (:maLoai = '' or s.loai.maLoai = :maLoai)
              and s.giaBan >= :min and s.giaBan <= :max
            """)
    List<SanPham> search(@Param("q") String q, @Param("maLoai") String maLoai,
                         @Param("min") BigDecimal min, @Param("max") BigDecimal max, Sort sort);

    boolean existsByLoaiMaLoai(String maLoai);
}
