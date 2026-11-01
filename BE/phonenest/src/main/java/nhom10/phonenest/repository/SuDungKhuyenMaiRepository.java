package nhom10.phonenest.repository;
import nhom10.phonenest.entity.SuDungKhuyenMai; import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SuDungKhuyenMaiRepository extends JpaRepository<SuDungKhuyenMai, String> {
    long countByKhuyenMaiMaKmAndKhachHangMaKh(String maKm, String maKh);
    boolean existsByKhuyenMaiMaKm(String maKm);
    List<SuDungKhuyenMai> findByDonHangMaDonHang(String maDonHang);
    List<SuDungKhuyenMai> findByDonHangMaDonHangAndKhuyenMaiMaKm(String maDonHang, String maKm);
    void deleteByDonHangMaDonHang(String maDonHang);
}
