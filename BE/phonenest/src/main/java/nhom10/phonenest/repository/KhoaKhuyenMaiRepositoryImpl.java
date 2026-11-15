package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.KhuyenMai;

import java.util.Optional;

class KhoaKhuyenMaiRepositoryImpl implements KhoaKhuyenMaiRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<KhuyenMai> findByIdForUpdate(String ma) {
        return KhoaNapMoi.khoa(em, "select k from KhuyenMai k where k.maKm = :ma", "ma", ma, KhuyenMai.class)
                .stream().findFirst();
    }
}
