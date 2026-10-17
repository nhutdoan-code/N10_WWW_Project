package nhom10.phonenest.repository;

import java.util.List;
import nhom10.phonenest.entity.DiaChi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaChiRepository extends JpaRepository<DiaChi, String> {
    List<DiaChi> findByMaKhOrderByMacDinhDescMaDiaChiAsc(String maKh);
}
