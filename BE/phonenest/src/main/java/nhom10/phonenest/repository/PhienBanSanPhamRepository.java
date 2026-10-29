package nhom10.phonenest.repository;
import nhom10.phonenest.entity.PhienBanSanPham; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PhienBanSanPhamRepository extends JpaRepository<PhienBanSanPham,String>{
    List<PhienBanSanPham> findBySanPhamMaSanPhamOrderByGiaBanAsc(String maSanPham);
    Optional<PhienBanSanPham> findBySku(String sku);
    boolean existsBySku(String sku);
}
