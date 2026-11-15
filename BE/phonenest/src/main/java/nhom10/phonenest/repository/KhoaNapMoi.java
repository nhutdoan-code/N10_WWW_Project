package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.List;

/**
 * 2B.7D: khoa dong (SELECT ... FOR UPDATE) VA nap lai trang thai moi nhat.
 *
 * <p>Loi da phat hien: truy van @Lock tra ve entity DA CO trong persistence context ma KHONG cap nhat
 * trang thai. Neu giao dich da doc bien the truoc do (vd. DonHangService.datHang goi
 * ThanhToanService.mucMua), so ton trong bo nho la gia tri cu -> tru kho ghi de mot lan nhap kho /
 * dieu chinh vua commit (mat cap nhat ton kho). Sau khi khoa, flush thay doi dang cho roi refresh.</p>
 */
final class KhoaNapMoi {

    private KhoaNapMoi() {
    }

    static <T> List<T> khoa(EntityManager em, String jpql, String ten, Object giaTri, Class<T> loai) {
        List<T> ds = em.createQuery(jpql, loai)
                .setParameter(ten, giaTri)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (!ds.isEmpty()) {
            em.flush();
            for (T x : ds) {
                em.refresh(x); // da giu khoa dong: doc lai gia tri da commit moi nhat
            }
        }
        return ds;
    }
}
