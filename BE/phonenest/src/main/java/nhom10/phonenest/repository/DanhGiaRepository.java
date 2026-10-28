package nhom10.phonenest.repository;
import nhom10.phonenest.entity.DanhGia; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface DanhGiaRepository extends JpaRepository<DanhGia,String>{
 List<DanhGia> findBySanPhamMaSanPhamOrderByNgayDanhGiaDesc(String ma); List<DanhGia> findByKhachHangMaKhOrderByNgayDanhGiaDesc(String ma); List<DanhGia> findAllByOrderByNgayDanhGiaDesc();
 Optional<DanhGia> findByChiTietDonHangMaChiTiet(String maChiTiet);
 @Query("select avg(d.diemSao) from DanhGia d where d.sanPham.maSanPham=:ma") Double trungBinh(@Param("ma") String ma);
}
