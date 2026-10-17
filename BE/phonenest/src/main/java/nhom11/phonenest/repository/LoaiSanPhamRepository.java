package nhom11.phonenest.repository;

import nhom11.phonenest.entity.LoaiSanPham;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoaiSanPhamRepository extends JpaRepository<LoaiSanPham, String> {

    boolean existsByMaLoaiCha(String maLoaiCha);
}
