package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PhienBanSanPham;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Khoa bien the + nap lai trang thai moi nhat (xem KhoaNapMoi). */
public interface KhoaPhienBanRepository {
    Optional<PhienBanSanPham> findByIdForUpdate(String id);

    List<PhienBanSanPham> findAllByIdForUpdate(Collection<String> ids);

    /** Khoa moi bien the cua mot san pham, theo thu tu ma co dinh (tranh deadlock). */
    List<PhienBanSanPham> findBySanPhamForUpdate(String maSanPham);
}
