package nhom10.phonenest.repository;
import nhom10.phonenest.entity.ChiTietDonHang; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang,String> {
    List<ChiTietDonHang> findByDonHangMaDonHang(String maDonHang);
    boolean existsByPhienBanMaPhienBan(String maPhienBan);
}
