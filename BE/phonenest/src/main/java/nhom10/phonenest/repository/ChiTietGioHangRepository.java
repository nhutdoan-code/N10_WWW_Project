package nhom10.phonenest.repository;

import nhom10.phonenest.entity.ChiTietGioHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChiTietGioHangRepository
        extends JpaRepository<ChiTietGioHang, ChiTietGioHang.Id> {

    List<ChiTietGioHang> findByGioHangMaGioHang(String maGioHang);

    boolean existsByPhienBan_SanPham_MaSanPham(String maSanPham);
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ChiTietGioHang c WHERE c.id.maGioHang = :maGioHang")
    int xoaTheoMaGioHang(@Param("maGioHang") String maGioHang);

}