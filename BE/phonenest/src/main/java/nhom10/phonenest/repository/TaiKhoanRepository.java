package nhom10.phonenest.repository;

import nhom10.phonenest.entity.TaiKhoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaiKhoanRepository extends JpaRepository<TaiKhoan, String> {

    Optional<TaiKhoan> findByEmailOrSoDienThoai(String email, String soDienThoai);

    Optional<TaiKhoan> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsBySoDienThoai(String soDienThoai);
}
