package nhom10.phonenest.repository;

import nhom10.phonenest.entity.KhuyenMai;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KhuyenMai k where k.maKm = :ma")
    Optional<KhuyenMai> findByIdForUpdate(@Param("ma") String ma);

    @Modifying
    @Query("update KhuyenMai k set k.daDung = k.daDung + 1 " +
            "where k.maKm = :ma and (k.soLuong is null or k.daDung < k.soLuong)")
    int tangDaDungNeuConLuot(@Param("ma") String ma);

    @Modifying
    @Query("update KhuyenMai k set k.daDung = case when k.daDung > 0 then k.daDung - 1 else 0 end " +
            "where k.maKm = :ma and k.daDung > 0")
    int hoanDaDung(@Param("ma") String ma);
}
