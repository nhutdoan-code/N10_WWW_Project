package nhom10.phonenest.repository;

import nhom10.phonenest.entity.KhuyenMai;

import java.util.Optional;

/** Khoa khuyen mai + nap lai trang thai moi nhat (xem KhoaNapMoi). */
public interface KhoaKhuyenMaiRepository {
    Optional<KhuyenMai> findByIdForUpdate(String ma);
}
