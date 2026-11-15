package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nhom10.phonenest.entity.DonHang;

import java.util.Optional;

class KhoaDonHangRepositoryImpl implements KhoaDonHangRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<DonHang> findByIdForUpdate(String ma) {
        return KhoaNapMoi.khoa(em, "select d from DonHang d where d.maDonHang = :ma", "ma", ma, DonHang.class)
                .stream().findFirst();
    }
}
