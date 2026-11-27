package nhom10.phonenest.repository;

import nhom10.phonenest.entity.NhatKyHoatDong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Audit log: chi insert (Phase 3) va doc co loc/phan trang (Phase 5).
 * JpaSpecificationExecutor duoc chuan bi cho bo loc dong o Phase 5.
 */
public interface NhatKyHoatDongRepository
        extends JpaRepository<NhatKyHoatDong, String>,
        JpaSpecificationExecutor<NhatKyHoatDong> {

    /** Lich su phe duyet cua mot bien the (moi nhat truoc). */
    List<NhatKyHoatDong> findTop200ByLoaiDoiTuongAndMaDoiTuongOrderByThoiGianDescMaNhatKyDesc(
            LoaiDoiTuongAudit loaiDoiTuong, String maDoiTuong);

    List<NhatKyHoatDong> findByLoaiDoiTuongAndMaDoiTuongOrderByThoiGianDescMaNhatKyDesc(
            LoaiDoiTuongAudit loaiDoiTuong, String maDoiTuong);

    /** Thao tac phe duyet gan nhat thuoc cac loai cho truoc (dung cho tach quyen mo khoa/duyet). */
    Optional<NhatKyHoatDong> findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongInOrderByThoiGianDescMaNhatKyDesc(
            LoaiDoiTuongAudit loaiDoiTuong, String maDoiTuong, Collection<HanhDongAudit> hanhDongs);
}
