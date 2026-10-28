package nhom10.phonenest.repository;
import nhom10.phonenest.entity.SanPham; import org.springframework.data.jpa.repository.JpaRepository;
public interface SanPhamRepository extends JpaRepository<SanPham,String>{ boolean existsByLoaiMaLoai(String maLoai); }
