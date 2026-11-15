package nhom10.phonenest.repository;
import nhom10.phonenest.entity.DanhGia; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface DanhGiaRepository extends JpaRepository<DanhGia,String>{
 List<DanhGia> findBySanPhamMaSanPhamOrderByNgayDanhGiaDesc(String ma); List<DanhGia> findByKhachHangMaKhOrderByNgayDanhGiaDesc(String ma); List<DanhGia> findAllByOrderByNgayDanhGiaDesc();
 Optional<DanhGia> findByChiTietDonHangMaChiTiet(String maChiTiet);
 @Query(value = "SELECT d FROM DanhGia d WHERE (:pattern IS NULL OR LOWER(d.maDanhGia) LIKE LOWER(:pattern) OR LOWER(d.noiDungBinhLuan) LIKE LOWER(:pattern) OR LOWER(d.sanPham.tenMay) LIKE LOWER(:pattern) OR LOWER(d.khachHang.hoTen) LIKE LOWER(:pattern)) ORDER BY d.ngayDanhGia DESC, d.maDanhGia DESC", countQuery = "SELECT COUNT(d) FROM DanhGia d WHERE (:pattern IS NULL OR LOWER(d.maDanhGia) LIKE LOWER(:pattern) OR LOWER(d.noiDungBinhLuan) LIKE LOWER(:pattern) OR LOWER(d.sanPham.tenMay) LIKE LOWER(:pattern) OR LOWER(d.khachHang.hoTen) LIKE LOWER(:pattern))")
 Page<DanhGia> searchAdminPage(@Param("pattern") String pattern, Pageable pageable);
 @Query("select avg(d.diemSao) from DanhGia d where d.sanPham.maSanPham=:ma") Double trungBinh(@Param("ma") String ma);
}
