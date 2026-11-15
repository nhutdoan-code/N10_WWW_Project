package nhom10.phonenest.repository;

import java.util.List;
import nhom10.phonenest.entity.HoiDap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoiDapRepository extends JpaRepository<HoiDap, String> {
    List<HoiDap> findByMaSanPhamOrderByNgayHoiDesc(String maSanPham);

    List<HoiDap> findByMaKhOrderByNgayHoiDesc(String maKh);

    List<HoiDap> findAllByOrderByNgayHoiDesc();

    @Query(value = """
        SELECT h FROM HoiDap h
        WHERE (:pattern IS NULL
            OR LOWER(h.maCauHoi) LIKE LOWER(:pattern)
            OR LOWER(h.noiDungCauHoi) LIKE LOWER(:pattern)
            OR LOWER(h.noiDungTraLoi) LIKE LOWER(:pattern)
            OR LOWER(h.maSanPham) LIKE LOWER(:pattern)
            OR h.maSanPham IN (SELECT s.maSanPham FROM SanPham s WHERE LOWER(s.tenMay) LIKE LOWER(:pattern))
            OR h.maKh IN (SELECT kh.maKh FROM KhachHang kh WHERE LOWER(kh.hoTen) LIKE LOWER(:pattern)))
        ORDER BY h.ngayHoi DESC, h.maCauHoi DESC
        """,
        countQuery = """
        SELECT COUNT(h) FROM HoiDap h
        WHERE (:pattern IS NULL
            OR LOWER(h.maCauHoi) LIKE LOWER(:pattern)
            OR LOWER(h.noiDungCauHoi) LIKE LOWER(:pattern)
            OR LOWER(h.noiDungTraLoi) LIKE LOWER(:pattern)
            OR LOWER(h.maSanPham) LIKE LOWER(:pattern)
            OR h.maSanPham IN (SELECT s.maSanPham FROM SanPham s WHERE LOWER(s.tenMay) LIKE LOWER(:pattern))
            OR h.maKh IN (SELECT kh.maKh FROM KhachHang kh WHERE LOWER(kh.hoTen) LIKE LOWER(:pattern)))
        """)
    Page<HoiDap> searchAdminPage(@Param("pattern") String pattern, Pageable pageable);
}
