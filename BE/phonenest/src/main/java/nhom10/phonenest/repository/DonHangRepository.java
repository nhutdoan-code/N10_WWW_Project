package nhom10.phonenest.repository;
import nhom10.phonenest.entity.DonHang; import nhom10.phonenest.enums.TrangThaiDonHang; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.List;
public interface DonHangRepository extends JpaRepository<DonHang,String>{
 List<DonHang> findByKhachHangMaKhOrderByNgayMuaDesc(String maKh); List<DonHang> findAllByOrderByNgayMuaDesc();
 boolean existsByKhachHangMaKh(String maKh); boolean existsByKhachHangMaKhAndTrangThaiNot(String maKh, TrangThaiDonHang t);
 @Query("select count(d)>0 from DonHang d join d.chiTiet c where c.phienBan.sanPham.maSanPham=:ma") boolean daCoTrongDonHang(@Param("ma") String ma);
}
