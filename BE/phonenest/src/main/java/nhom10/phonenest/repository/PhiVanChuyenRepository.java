package nhom10.phonenest.repository;

import java.util.Optional;
import nhom10.phonenest.entity.PhiVanChuyen;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhiVanChuyenRepository extends JpaRepository<PhiVanChuyen, String> {
    Optional<PhiVanChuyen> findByMaTinh(String maTinh);

    Optional<PhiVanChuyen> findByMaTinhIsNull();

    boolean existsByMaTinh(String maTinh);
}
