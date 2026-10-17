package nhom11.phonenest.repository;

import nhom11.phonenest.entity.GioHang;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GioHangRepository extends JpaRepository<GioHang, String> {

    Optional<GioHang> findByMaKh(String maKh);
}
