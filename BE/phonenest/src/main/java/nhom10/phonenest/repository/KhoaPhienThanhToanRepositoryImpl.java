package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.PhienThanhToan;

import java.util.Optional;

class KhoaPhienThanhToanRepositoryImpl implements KhoaPhienThanhToanRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<PhienThanhToan> findByMaPhienThanhToanForUpdate(String maPhienThanhToan) {
        return KhoaNapMoi.khoa(
                        entityManager,
                        "select p from PhienThanhToan p where p.maPhienThanhToan = :ma",
                        "ma",
                        maPhienThanhToan,
                        PhienThanhToan.class
                )
                .stream()
                .findFirst();
    }
}
