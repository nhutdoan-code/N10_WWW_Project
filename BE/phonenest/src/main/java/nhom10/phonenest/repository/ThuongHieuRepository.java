package nhom10.phonenest.repository;
import nhom10.phonenest.entity.ThuongHieu; import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThuongHieuRepository extends JpaRepository<ThuongHieu, String> {
    // [THÊM MỚI] Lấy các thương hiệu đang hoạt động
    List<ThuongHieu> findByTrangThaiHoatDongTrue();
}
