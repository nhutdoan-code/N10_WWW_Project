package nhom10.phonenest.repository;

import nhom10.phonenest.entity.GioHang;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GioHangRepository
        extends JpaRepository<GioHang, String> {

    Optional<GioHang> findByKhachHangMaKh(
            String maKhachHang
    );
}