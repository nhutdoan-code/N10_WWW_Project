package nhom10.phonenest.repository;
import nhom10.phonenest.entity.BienDongKho; import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BienDongKhoRepository extends JpaRepository<BienDongKho, String> {
    boolean existsByPhienBanMaPhienBan(String maPhienBan);
    List<BienDongKho> findByPhienBanMaPhienBanOrderByThoiGianDesc(String maPhienBan);
    List<BienDongKho> findAllByOrderByThoiGianDesc();
}
