package nhom10.phonenest.repository;

import nhom10.phonenest.entity.ThanhToan;

import java.util.Optional;

/** Khoa giao dich thanh toan + nap lai trang thai moi nhat (xem KhoaNapMoi). 2B.7E. */
public interface KhoaThanhToanRepository {
    Optional<ThanhToan> findByMaGiaoDichForUpdate(String ma);
}
