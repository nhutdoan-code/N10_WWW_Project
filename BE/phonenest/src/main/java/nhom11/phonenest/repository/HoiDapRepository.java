package nhom11.phonenest.repository;

import java.util.List;
import nhom11.phonenest.entity.HoiDap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoiDapRepository extends JpaRepository<HoiDap, String> {
    List<HoiDap> findByMaSanPhamOrderByNgayHoiDesc(String maSanPham);

    List<HoiDap> findByMaKhOrderByNgayHoiDesc(String maKh);

    List<HoiDap> findAllByOrderByNgayHoiDesc();
}
