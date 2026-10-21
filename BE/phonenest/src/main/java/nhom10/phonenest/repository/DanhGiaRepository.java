package nhom10.phonenest.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

import nhom10.phonenest.entity.DanhGia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DanhGiaRepository extends JpaRepository<DanhGia, String> {
    List<DanhGia> findByMaSanPhamOrderByNgayDanhGiaDesc(String maSanPham);

    List<DanhGia> findByMaKhOrderByNgayDanhGiaDesc(String maKh);

    List<DanhGia> findAllByOrderByNgayDanhGiaDesc();

    Optional<DanhGia> findByMaDonHangAndMaSanPham(String maDonHang, String maSanPham);

    @Query("select avg(d.diemSao) from DanhGia d where d.maSanPham = ?1")
    Double trungBinh(String maSanPham);
}
