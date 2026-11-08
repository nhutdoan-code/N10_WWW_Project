package nhom10.phonenest.repository;

import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HanhViNguoiDungRepository
        extends JpaRepository<HanhViNguoiDung, String>,
        JpaSpecificationExecutor<HanhViNguoiDung> {

    // --- PR #18 Query Methods ---

    boolean existsBySessionIdAndMaSanPhamAndLoaiHanhViAndThoiGianAfter(
            String sessionId,
            String maSanPham,
            LoaiHanhViNguoiDung loaiHanhVi,
            LocalDateTime after
    );

    boolean existsBySessionIdAndMaSanPhamAndLoaiHanhVi(
            String sessionId,
            String maSanPham,
            LoaiHanhViNguoiDung loaiHanhVi
    );

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN TRUE ELSE FALSE END FROM HanhViNguoiDung h " +
           "WHERE h.loaiHanhVi = :loai AND h.maSanPham = :maSanPham AND h.thoiGian >= :after " +
           "AND ((:maKh IS NOT NULL AND h.maKh = :maKh) OR (:sessionId IS NOT NULL AND h.sessionId = :sessionId))")
    boolean existsRecentView(
            @Param("maKh") String maKh,
            @Param("sessionId") String sessionId,
            @Param("maSanPham") String maSanPham,
            @Param("loai") LoaiHanhViNguoiDung loai,
            @Param("after") LocalDateTime after
    );

    List<HanhViNguoiDung> findByMaKhOrderByThoiGianDesc(String maKh, Pageable pageable);

    List<HanhViNguoiDung> findBySessionIdOrderByThoiGianDesc(String sessionId, Pageable pageable);

    List<HanhViNguoiDung> findAllByOrderByThoiGianDesc(Pageable pageable);

    @Query("SELECT h.loaiHanhVi, COUNT(h) FROM HanhViNguoiDung h WHERE (:tuNgay IS NULL OR h.thoiGian >= :tuNgay) AND (:denNgay IS NULL OR h.thoiGian <= :denNgay) AND (:maKh IS NULL OR h.maKh = :maKh) GROUP BY h.loaiHanhVi")
    List<Object[]> countGroupedByLoaiHanhVi(
            @Param("tuNgay") LocalDateTime tuNgay,
            @Param("denNgay") LocalDateTime denNgay,
            @Param("maKh") String maKh
    );

    @Query("SELECT h.maSanPham, COUNT(h) FROM HanhViNguoiDung h WHERE h.loaiHanhVi = :loai AND h.maSanPham IS NOT NULL AND (:tuNgay IS NULL OR h.thoiGian >= :tuNgay) AND (:denNgay IS NULL OR h.thoiGian <= :denNgay) GROUP BY h.maSanPham ORDER BY COUNT(h) DESC")
    List<Object[]> findTopProductsByLoai(
            @Param("loai") LoaiHanhViNguoiDung loai,
            @Param("tuNgay") LocalDateTime tuNgay,
            @Param("denNgay") LocalDateTime denNgay,
            Pageable pageable
    );

    @Query("SELECT LOWER(TRIM(h.tuKhoa)), COUNT(h) FROM HanhViNguoiDung h WHERE h.loaiHanhVi = nhom10.phonenest.enums.LoaiHanhViNguoiDung.SEARCH AND h.tuKhoa IS NOT NULL AND TRIM(h.tuKhoa) <> '' AND (:tuNgay IS NULL OR h.thoiGian >= :tuNgay) AND (:denNgay IS NULL OR h.thoiGian <= :denNgay) GROUP BY LOWER(TRIM(h.tuKhoa)) ORDER BY COUNT(h) DESC")
    List<Object[]> findTopKeywords(
            @Param("tuNgay") LocalDateTime tuNgay,
            @Param("denNgay") LocalDateTime denNgay,
            Pageable pageable
    );

    @Query("SELECT DISTINCT h.tuKhoa FROM HanhViNguoiDung h WHERE h.maKh = :maKh AND h.loaiHanhVi = nhom10.phonenest.enums.LoaiHanhViNguoiDung.SEARCH AND h.tuKhoa IS NOT NULL AND TRIM(h.tuKhoa) <> '' ORDER BY h.thoiGian DESC")
    List<String> findRecentKeywordsByMaKh(@Param("maKh") String maKh, Pageable pageable);

    @Query("SELECT h.maSanPham, COUNT(h) FROM HanhViNguoiDung h WHERE h.maKh = :maKh AND h.maSanPham IS NOT NULL GROUP BY h.maSanPham ORDER BY COUNT(h) DESC")
    List<Object[]> findInteractedProductsByMaKh(@Param("maKh") String maKh, Pageable pageable);

    long countByMaKh(String maKh);

    @Modifying
    @Query("DELETE FROM HanhViNguoiDung h WHERE h.thoiGian < :mocThoiGian")
    int deleteByThoiGianBefore(@Param("mocThoiGian") LocalDateTime mocThoiGian);

    // --- PR #17 Compatibility Methods ---

    @Query("""
            SELECT h FROM HanhViNguoiDung h
            WHERE h.thoiGian >= :since
              AND (h.sessionId = :visitorId OR (:maTk IS NOT NULL AND h.maKh = :maTk))
            ORDER BY h.thoiGian DESC
            """)
    List<HanhViNguoiDung> findRecent(
            @Param("maTk") String maTk,
            @Param("visitorId") String visitorId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM HanhViNguoiDung h WHERE h.thoiGian < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
