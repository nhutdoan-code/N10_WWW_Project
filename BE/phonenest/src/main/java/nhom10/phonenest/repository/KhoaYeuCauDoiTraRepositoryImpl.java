package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.YeuCauDoiTra;

import java.util.Optional;

class KhoaYeuCauDoiTraRepositoryImpl implements KhoaYeuCauDoiTraRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<YeuCauDoiTra> findByIdForUpdate(String ma) {
        return KhoaNapMoi.khoa(em, "select y from YeuCauDoiTra y where y.maYeuCau = :ma", "ma", ma, YeuCauDoiTra.class)
                .stream().findFirst();
    }
}
