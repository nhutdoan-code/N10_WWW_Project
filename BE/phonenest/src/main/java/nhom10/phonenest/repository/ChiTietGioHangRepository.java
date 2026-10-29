package nhom10.phonenest.repository;

import nhom10.phonenest.entity.ChiTietGioHang;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChiTietGioHangRepository
        extends JpaRepository<ChiTietGioHang, ChiTietGioHang.Id> {

    List<ChiTietGioHang> findByGioHangMaGioHang(String maGioHang);

    boolean existsBySanPhamMaSanPham(String maSanPham);
}