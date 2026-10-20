package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhiVanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhiVanChuyenRepository extends JpaRepository<PhiVanChuyen, String> {
    boolean existsByKhuVucIgnoreCase(String khuVuc);
}
