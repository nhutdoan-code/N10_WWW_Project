package nhom10.phonenest.repository;

import nhom10.phonenest.entity.NhatKyHoatDong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Audit log: chi insert (Phase 3) va doc co loc/phan trang (Phase 5).
 * JpaSpecificationExecutor duoc chuan bi cho bo loc dong o Phase 5.
 */
public interface NhatKyHoatDongRepository
        extends JpaRepository<NhatKyHoatDong, String>,
        JpaSpecificationExecutor<NhatKyHoatDong> {
}
