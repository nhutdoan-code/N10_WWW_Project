package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.PhienBanSanPham;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

class KhoaPhienBanRepositoryImpl implements KhoaPhienBanRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<PhienBanSanPham> findByIdForUpdate(String id) {
        return KhoaNapMoi.khoa(em, "SELECT p FROM PhienBanSanPham p WHERE p.maPhienBan = :id", "id", id, PhienBanSanPham.class)
                .stream().findFirst();
    }

    @Override
    public List<PhienBanSanPham> findAllByIdForUpdate(Collection<String> ids) {
        return KhoaNapMoi.khoa(em, "SELECT p FROM PhienBanSanPham p WHERE p.maPhienBan IN :ids ORDER BY p.maPhienBan",
                "ids", ids, PhienBanSanPham.class);
    }

    @Override
    public List<PhienBanSanPham> findBySanPhamForUpdate(String maSanPham) {
        return KhoaNapMoi.khoa(em, "SELECT p FROM PhienBanSanPham p WHERE p.sanPham.maSanPham = :ma ORDER BY p.maPhienBan",
                "ma", maSanPham, PhienBanSanPham.class);
    }
}
