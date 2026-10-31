package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhienBanSanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PhienBanSanPhamRepository
        extends JpaRepository<PhienBanSanPham, String> {

    List<PhienBanSanPham> findBySanPhamMaSanPhamOrderByGiaBanAsc(String maSanPham);

    List<PhienBanSanPham> findBySanPham_MaSanPham(String maSanPham);

    Optional<PhienBanSanPham> findBySku(String sku);

    boolean existsBySku(String sku);
}