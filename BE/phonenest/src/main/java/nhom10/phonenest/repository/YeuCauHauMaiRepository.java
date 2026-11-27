package nhom10.phonenest.repository;

import nhom10.phonenest.entity.YeuCauHauMai;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiHauMai;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YeuCauHauMaiRepository extends JpaRepository<YeuCauHauMai, String> {
    @Query("select y from YeuCauHauMai y left join y.donHang d left join d.khachHang k left join y.khachHang own where own.maKh = :maKh or (own.maKh is null and k.maKh = :maKh) order by y.ngayTao desc")
    List<YeuCauHauMai> findByDonHangKhachHangMaKhOrderByNgayTaoDesc(@Param("maKh") String maKh);

    List<YeuCauHauMai> findAllByOrderByNgayTaoDesc();

    boolean existsByDonHangMaDonHangAndLoaiAndTrangThaiIn(
            String maDonHang, LoaiHauMai loai, Collection<TrangThaiHauMai> trangThai);

    boolean existsByDonHangMaDonHangAndChiTietDonHangMaChiTietAndLoaiAndTrangThaiIn(
            String maDonHang, String maChiTiet, LoaiHauMai loai,
            Collection<TrangThaiHauMai> trangThai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select y from YeuCauHauMai y where y.maYeuCau = :ma")
    Optional<YeuCauHauMai> findByIdForUpdate(@Param("ma") String ma);
}
