package nhom10.phonenest.repository;

import nhom10.phonenest.entity.TaiKhoan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaiKhoanRepository extends JpaRepository<TaiKhoan, String> {

    Optional<TaiKhoan> findByEmailOrSoDienThoai(String email, String soDienThoai);

    Optional<TaiKhoan> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsBySoDienThoai(String soDienThoai);

    @Query("SELECT t FROM TaiKhoan t " +
           "WHERE LOWER(t.email) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(t.soDienThoai) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR LOWER(t.maTk) LIKE LOWER(:pattern) ESCAPE '\\' " +
           "   OR t.maTk IN (SELECT kh.maKh FROM KhachHang kh WHERE LOWER(kh.hoTen) LIKE LOWER(:pattern) ESCAPE '\\') " +
           "   OR t.maTk IN (SELECT qtv.maQtv FROM QuanTriVien qtv WHERE LOWER(qtv.hoTen) LIKE LOWER(:pattern) ESCAPE '\\') " +
           "ORDER BY t.ngayTao DESC")
    List<TaiKhoan> searchAdmin(@Param("pattern") String pattern, Pageable pageable);
}
