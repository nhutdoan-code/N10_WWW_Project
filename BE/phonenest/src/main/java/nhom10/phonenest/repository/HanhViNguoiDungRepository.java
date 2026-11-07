package nhom10.phonenest.repository;

import nhom10.phonenest.entity.HanhViNguoiDung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HanhViNguoiDungRepository extends JpaRepository<HanhViNguoiDung, String> {

    @Query("""
            SELECT h FROM HanhViNguoiDung h
            WHERE h.thoiGian >= :since
              AND (h.visitorId = :visitorId OR (:maTk IS NOT NULL AND h.maTk = :maTk))
            ORDER BY h.thoiGian DESC
            """)
    List<HanhViNguoiDung> findRecent(
            @Param("maTk") String maTk,
            @Param("visitorId") String visitorId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM HanhViNguoiDung h WHERE h.thoiGian < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
