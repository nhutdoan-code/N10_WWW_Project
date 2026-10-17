package nhom11.phonenest.repository;

import java.util.List;
import nhom11.phonenest.entity.DiaChi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaChiRepository extends JpaRepository<DiaChi, String> {
    List<DiaChi> findByMaKhOrderByMacDinhDescMaDiaChiAsc(String maKh);
}
