package nhom10.phonenest.repository;

import java.util.List;
import nhom10.phonenest.entity.HoiDap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoiDapRepository extends JpaRepository<HoiDap, String> {
    List<HoiDap> findByMaSanPhamOrderByNgayHoiDesc(String maSanPham);

    List<HoiDap> findByMaKhOrderByNgayHoiDesc(String maKh);

    List<HoiDap> findAllByOrderByNgayHoiDesc();
}
