package nhom10.phonenest.repository;

import java.util.List;
import nhom10.phonenest.entity.PhuongXa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhuongXaRepository extends JpaRepository<PhuongXa, String> {
    List<PhuongXa> findByMaTinhOrderByTenPhuongAsc(String maTinh);
}
