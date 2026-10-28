package nhom10.phonenest.repository;
import nhom10.phonenest.entity.YeuCauDoiTra; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface YeuCauDoiTraRepository extends JpaRepository<YeuCauDoiTra,String>{ List<YeuCauDoiTra> findByDonHangMaDonHangOrderByNgayTaoDesc(String maDonHang); }
