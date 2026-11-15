package nhom10.phonenest.repository;

import nhom10.phonenest.entity.DonHang;

import java.util.Optional;

/** Khoa don hang + nap lai trang thai moi nhat (xem KhoaNapMoi). */
public interface KhoaDonHangRepository {
    Optional<DonHang> findByIdForUpdate(String ma);
}
