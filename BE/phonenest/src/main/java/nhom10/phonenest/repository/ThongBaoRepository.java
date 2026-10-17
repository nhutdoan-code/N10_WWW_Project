package nhom10.phonenest.repository;

import java.util.List;
import nhom10.phonenest.entity.ThongBao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThongBaoRepository extends JpaRepository<ThongBao, String> {
    List<ThongBao> findByMaTkOrderByNgayTaoDesc(String maTk);

    List<ThongBao> findByMaTkAndDaDocFalse(String maTk);
}
