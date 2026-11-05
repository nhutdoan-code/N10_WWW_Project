package nhom10.phonenest.repository;

import nhom10.phonenest.entity.SuDungKhuyenMai;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuDungKhuyenMaiRepository extends JpaRepository<SuDungKhuyenMai, String> {
    long countByKhuyenMaiMaKmAndKhachHangMaKh(String maKm, String maKh);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from SuDungKhuyenMai s where s.khuyenMai.maKm = :maKm and s.khachHang.maKh = :maKh")
    List<SuDungKhuyenMai> findByKhuyenMaiMaKmAndKhachHangMaKhForCheck(@Param("maKm") String maKm, @Param("maKh") String maKh);

    boolean existsByKhuyenMaiMaKm(String maKm);
    List<SuDungKhuyenMai> findByDonHangMaDonHang(String maDonHang);
    List<SuDungKhuyenMai> findByDonHangMaDonHangAndKhuyenMaiMaKm(String maDonHang, String maKm);
    void deleteByDonHangMaDonHang(String maDonHang);
}
