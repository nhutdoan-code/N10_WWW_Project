package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.ThanhToan;

import java.util.Optional;

class KhoaThanhToanRepositoryImpl implements KhoaThanhToanRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<ThanhToan> findByMaGiaoDichForUpdate(String ma) {
        return KhoaNapMoi.khoa(em, "select t from ThanhToan t where t.maGiaoDich = :ma", "ma", ma, ThanhToan.class)
                .stream().findFirst();
    }
}
