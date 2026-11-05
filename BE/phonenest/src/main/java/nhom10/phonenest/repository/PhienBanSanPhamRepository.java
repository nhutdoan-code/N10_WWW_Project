package nhom10.phonenest.repository;

import jakarta.persistence.LockModeType;
import nhom10.phonenest.entity.PhienBanSanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhienBanSanPhamRepository extends JpaRepository<PhienBanSanPham, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PhienBanSanPham p WHERE p.maPhienBan = :id")
    Optional<PhienBanSanPham> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PhienBanSanPham p WHERE p.maPhienBan IN :ids")
    List<PhienBanSanPham> findAllByIdForUpdate(@Param("ids") Collection<String> ids);

    List<PhienBanSanPham> findBySanPhamMaSanPham(String maSanPham);

    List<PhienBanSanPham> findBySanPhamMaSanPhamOrderByGiaBanAsc(String maSanPham);

    List<PhienBanSanPham> findBySanPham_MaSanPham(String maSanPham);

    Optional<PhienBanSanPham> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT " +
           "  SUM(CASE WHEN p.soLuongTon = 0 THEN 1 ELSE 0 END), " +
           "  SUM(CASE WHEN p.soLuongTon > 0 AND p.soLuongTon <= :nguong THEN 1 ELSE 0 END), " +
           "  SUM(CASE WHEN p.soLuongTon > :nguong THEN 1 ELSE 0 END) " +
           "FROM PhienBanSanPham p " +
           "WHERE p.trangThai != nhom10.phonenest.enums.TrangThaiSanPham.NGUNG_KINH_DOANH " +
           "  AND p.sanPham.trangThai != nhom10.phonenest.enums.TrangThaiSanPham.NGUNG_KINH_DOANH")
    List<Object[]> countVariantInventorySummary(@Param("nguong") int nguong);
}
