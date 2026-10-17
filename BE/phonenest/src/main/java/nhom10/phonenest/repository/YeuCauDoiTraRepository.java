package nhom10.phonenest.repository;

import java.util.List;
import java.util.Optional;
import nhom10.phonenest.entity.YeuCauDoiTra;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YeuCauDoiTraRepository extends JpaRepository<YeuCauDoiTra, String> {
    Optional<YeuCauDoiTra> findByMaDonHang(String maDonHang);

    List<YeuCauDoiTra> findAllByOrderByNgayTaoDesc();
}
