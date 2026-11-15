package nhom10.phonenest.repository;

import nhom10.phonenest.entity.YeuCauDoiTra;

import java.util.Optional;

/** Khoa yeu cau doi tra + nap lai trang thai moi nhat (xem KhoaNapMoi). 2B.7E. */
public interface KhoaYeuCauDoiTraRepository {
    Optional<YeuCauDoiTra> findByIdForUpdate(String ma);
}
