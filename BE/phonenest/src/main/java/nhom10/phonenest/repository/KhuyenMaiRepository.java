package nhom10.phonenest.repository;

import nhom10.phonenest.entity.KhuyenMai;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, String>, KhoaKhuyenMaiRepository {
    // 2B.7D: findByIdForUpdate nam o KhoaKhuyenMaiRepository (khoa + nap lai trang thai moi nhat).
    @Query(value = """
        SELECT k FROM KhuyenMai k
        WHERE (:pattern IS NULL OR LOWER(k.maKm) LIKE LOWER(:pattern) OR LOWER(k.tenKm) LIKE LOWER(:pattern))
        ORDER BY k.ngayBatDau DESC, k.maKm DESC
        """,
        countQuery = """
        SELECT COUNT(k) FROM KhuyenMai k
        WHERE (:pattern IS NULL OR LOWER(k.maKm) LIKE LOWER(:pattern) OR LOWER(k.tenKm) LIKE LOWER(:pattern))
        """)
    Page<KhuyenMai> searchAdminPage(@Param("pattern") String pattern, Pageable pageable);


    @Modifying
    @Query("update KhuyenMai k set k.daDung = k.daDung + 1 " +
            "where k.maKm = :ma and (k.soLuong is null or k.daDung < k.soLuong)")
    int tangDaDungNeuConLuot(@Param("ma") String ma);

    @Modifying
    @Query("update KhuyenMai k set k.daDung = case when k.daDung > 0 then k.daDung - 1 else 0 end " +
            "where k.maKm = :ma and k.daDung > 0")
    int hoanDaDung(@Param("ma") String ma);
}
