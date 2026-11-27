package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhanCongHauMai;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhanCongHauMaiRepository extends JpaRepository<PhanCongHauMai, String> {
    Optional<PhanCongHauMai> findByNguonAndMaYeuCau(String nguon, String maYeuCau);

    List<PhanCongHauMai> findByNguonAndMaYeuCauIn(String nguon, Collection<String> maYeuCau);
}
