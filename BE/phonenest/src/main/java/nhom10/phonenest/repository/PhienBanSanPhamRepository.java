package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhienBanSanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhienBanSanPhamRepository extends JpaRepository<PhienBanSanPham, String> {

    List<PhienBanSanPham> findBySanPhamMaSanPhamOrderByGiaBanAsc(String maSanPham);

    Optional<PhienBanSanPham> findBySku(String sku);

    boolean existsBySku(String sku);

    // ==========================================
    // Phase 3: Variant Inventory Summary Query
    // ==========================================

    @Query("SELECT " +
           "COALESCE(SUM(CASE WHEN p.soLuongTon = 0 THEN 1 ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN p.soLuongTon > 0 AND p.soLuongTon <= :nguong THEN 1 ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN p.soLuongTon > :nguong THEN 1 ELSE 0 END), 0) " +
           "FROM PhienBanSanPham p " +
           "WHERE p.trangThai = nhom10.phonenest.enums.TrangThaiSanPham.DANG_KINH_DOANH " +
           "  AND p.sanPham.trangThai != nhom10.phonenest.enums.TrangThaiSanPham.NGUNG_KINH_DOANH")
    List<Object[]> countVariantInventorySummary(@Param("nguong") int nguong);
}
