package nhom11.phonenest.repository;

import nhom11.phonenest.entity.DonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DonHangRepository extends JpaRepository<DonHang, String> {

    List<DonHang> findByMaKhOrderByNgayMuaDesc(String maKh);

    List<DonHang> findAllByOrderByNgayMuaDesc();

    boolean existsByMaKh(String maKh);

    @Query("select count(d) > 0 from DonHang d join d.chiTiet c where c.maSanPham = :ma")
    boolean daCoTrongDonHang(@Param("ma") String maSanPham);
}
