package nhom10.phonenest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.NhatKyHoatDongChiTietDto;
import nhom10.phonenest.dto.NhatKyHoatDongDto;
import nhom10.phonenest.dto.TrangDto;
import nhom10.phonenest.entity.NhatKyHoatDong;
import nhom10.phonenest.entity.QuanTriVien;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.NhatKyHoatDongRepository;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.util.AuditDiffUtil;
import nhom10.phonenest.util.IdGenerator;
import nhom10.phonenest.util.RequestInfoUtil;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ghi NhatKyHoatDong cho thao tac quan tri.
 *
 * <p>Audit log thuoc cung transaction voi nghiep vu: method ghi dung
 * {@link Propagation#MANDATORY}, khong mo transaction rieng va khong nuot loi
 * persistence. Nếu nghiep vu rollback thi audit cung rollback.
 *
 * <p>Phase 3 moi chi cung cap infrastructure; chua tich hop vao business service.
 */
@Service
@RequiredArgsConstructor
public class NhatKyHoatDongService {

    private static final int MAX_MO_TA = 500;
    private static final int MAX_TEN_NGUOI_THUC_HIEN = 150;

    private final NhatKyHoatDongRepository nhatKyHoatDongRepository;
    private final QuanTriVienRepository quanTriVienRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final AuditDiffUtil auditDiffUtil;
    private final ObjectMapper objectMapper;

    /**
     * Ghi mot dong audit trong transaction cua caller.
     *
     * @return true neu da insert, false neu khong co thay doi (no-op).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean ghi(HanhDongAudit hanhDong,
                       LoaiDoiTuongAudit loaiDoiTuong,
                       String maDoiTuong,
                       Map<String, ?> duLieuTruoc,
                       Map<String, ?> duLieuSau) {
        Objects.requireNonNull(hanhDong, "hanhDong không được null");
        Objects.requireNonNull(loaiDoiTuong, "loaiDoiTuong không được null");

        AuditDiffUtil.KetQuaDiff diff = auditDiffUtil.tinhDiff(duLieuTruoc, duLieuSau);
        if (!diff.coThayDoi()) {
            return false;
        }

        // Actor luon lay tu SecurityContext do backend xac thuc, khong nhan tu request body.
        String maTk = SecurityUtil.currentUserId();
        RequestInfoUtil.ThongTinYeuCau requestInfo = RequestInfoUtil.hienTai();

        NhatKyHoatDong nhatKy = new NhatKyHoatDong();
        nhatKy.setMaNhatKy(IdGenerator.next("NK"));
        nhatKy.setMaTk(maTk);
        nhatKy.setTenNguoiThucHien(tenNguoiThucHien(maTk));
        nhatKy.setHanhDong(hanhDong);
        nhatKy.setLoaiDoiTuong(loaiDoiTuong);
        nhatKy.setMaDoiTuong(maDoiTuong);
        nhatKy.setMoTa(taoMoTa(hanhDong, maDoiTuong));
        nhatKy.setDuLieuTruoc(diff.duLieuTruoc());
        nhatKy.setDuLieuSau(diff.duLieuSau());
        nhatKy.setIpAddress(requestInfo.ipAddress());
        nhatKy.setRequestMethod(requestInfo.requestMethod());
        nhatKy.setRequestPath(requestInfo.requestPath());
        nhatKy.setThoiGian(LocalDateTime.now());

        nhatKyHoatDongRepository.save(nhatKy);
        return true;
    }

    /**
     * Uu tien ten quan tri vien, sau do email tai khoan, cuoi cung la maTk.
     */
    private String tenNguoiThucHien(String maTk) {
        String hoTen = quanTriVienRepository.findById(maTk)
                .map(QuanTriVien::getHoTen)
                .filter(ten -> !ten.isBlank())
                .orElse(null);
        if (hoTen != null) {
            return cat(hoTen, MAX_TEN_NGUOI_THUC_HIEN);
        }
        String email = taiKhoanRepository.findById(maTk)
                .map(TaiKhoan::getEmail)
                .filter(ten -> !ten.isBlank())
                .orElse(null);
        return email != null ? cat(email, MAX_TEN_NGUOI_THUC_HIEN) : cat(maTk, MAX_TEN_NGUOI_THUC_HIEN);
    }

    private String taoMoTa(HanhDongAudit hanhDong, String maDoiTuong) {
        String moTa = switch (hanhDong) {
            case CREATE_PRODUCT -> "Tạo sản phẩm";
            case UPDATE_PRODUCT -> "Cập nhật sản phẩm";
            case DELETE_PRODUCT -> "Xóa sản phẩm";

            case CREATE_CATEGORY -> "Tạo danh mục";
            case UPDATE_CATEGORY -> "Cập nhật danh mục";
            case DELETE_CATEGORY -> "Xóa danh mục";

            case STOCK_IMPORT -> "Nhập kho";
            case STOCK_ADJUSTMENT -> "Điều chỉnh tồn kho";

            case CHANGE_ORDER_STATUS -> "Cập nhật trạng thái đơn hàng";
            case UPDATE_ORDER_ITEM -> "Cập nhật chi tiết đơn hàng";

            case LOCK_ACCOUNT -> "Khóa tài khoản";
            case UNLOCK_ACCOUNT -> "Mở khóa tài khoản";
            case UPDATE_ACCOUNT -> "Cập nhật tài khoản";
            case DELETE_ACCOUNT -> "Xóa tài khoản";

            case CREATE_PROMOTION -> "Tạo khuyến mãi";
            case UPDATE_PROMOTION -> "Cập nhật khuyến mãi";
            case DELETE_PROMOTION -> "Xóa khuyến mãi";

            case CREATE_SHIPPING_FEE -> "Tạo phí vận chuyển";
            case UPDATE_SHIPPING_FEE -> "Cập nhật phí vận chuyển";
            case DELETE_SHIPPING_FEE -> "Xóa phí vận chuyển";

            case ANSWER_QUESTION -> "Trả lời câu hỏi";
            case DELETE_QUESTION -> "Xóa câu hỏi";

            case DELETE_REVIEW -> "Xóa đánh giá";

            case CHANGE_RETURN_STATUS -> "Cập nhật trạng thái yêu cầu đổi trả";
        };
        if (maDoiTuong != null && !maDoiTuong.isBlank()) {
            moTa = moTa + " " + maDoiTuong.trim();
        }
        return cat(moTa, MAX_MO_TA);
    }

    private String cat(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public NhatKyHoatDongDto toDto(NhatKyHoatDong nhatKy) {
        return new NhatKyHoatDongDto(
                nhatKy.getMaNhatKy(),
                nhatKy.getMaTk(),
                nhatKy.getTenNguoiThucHien(),
                nhatKy.getHanhDong(),
                nhatKy.getLoaiDoiTuong(),
                nhatKy.getMaDoiTuong(),
                nhatKy.getMoTa(),
                nhatKy.getIpAddress(),
                nhatKy.getRequestMethod(),
                nhatKy.getRequestPath(),
                nhatKy.getThoiGian());
    }

    public NhatKyHoatDongChiTietDto toChiTietDto(NhatKyHoatDong nhatKy) {
        return new NhatKyHoatDongChiTietDto(
                nhatKy.getMaNhatKy(),
                nhatKy.getMaTk(),
                nhatKy.getTenNguoiThucHien(),
                nhatKy.getHanhDong(),
                nhatKy.getLoaiDoiTuong(),
                nhatKy.getMaDoiTuong(),
                nhatKy.getMoTa(),
                nhatKy.getIpAddress(),
                nhatKy.getRequestMethod(),
                nhatKy.getRequestPath(),
                nhatKy.getThoiGian(),
                docJson(nhatKy.getDuLieuTruoc()),
                docJson(nhatKy.getDuLieuSau()));
    }

    /**
     * JSON hop le duoc tra ve dang JsonNode (object/array/...).
     * Du lieu legacy/hong duoc fallback thanh TextNode de API khong 500.
     */
    private JsonNode docJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(json);
        }
    }

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "thoiGian",
            "maNhatKy",
            "maTk",
            "tenNguoiThucHien",
            "hanhDong",
            "loaiDoiTuong",
            "maDoiTuong"
    );

    @Transactional(readOnly = true)
    public TrangDto<NhatKyHoatDongDto> list(
            int page,
            int size,
            String sort,
            String direction,
            LocalDateTime from,
            LocalDateTime to,
            String actor,
            String action,
            String entity,
            String keyword
    ) {
        if (page < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số trang không được âm");
        }
        if (size < 1 || size > 100) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Kích thước trang phải từ 1 đến 100");
        }

        String sortField = (sort == null || sort.isBlank()) ? "thoiGian" : sort.trim();
        if (!ALLOWED_SORTS.contains(sortField)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Trường sắp xếp không hợp lệ: " + sort);
        }

        String dirStr = (direction == null || direction.isBlank()) ? "desc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(dirStr) && !"desc".equals(dirStr)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Hướng sắp xếp không hợp lệ: " + direction);
        }
        Sort.Direction dir = "asc".equals(dirStr) ? Sort.Direction.ASC : Sort.Direction.DESC;

        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Thời gian bắt đầu (from) không được sau thời gian kết thúc (to)");
        }

        final HanhDongAudit hanhDongEnum;
        if (action != null && !action.isBlank()) {
            try {
                hanhDongEnum = HanhDongAudit.valueOf(action.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Hành động audit không hợp lệ: " + action);
            }
        } else {
            hanhDongEnum = null;
        }

        final LoaiDoiTuongAudit loaiDoiTuongEnum;
        if (entity != null && !entity.isBlank()) {
            try {
                loaiDoiTuongEnum = LoaiDoiTuongAudit.valueOf(entity.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Loại đối tượng audit không hợp lệ: " + entity);
            }
        } else {
            loaiDoiTuongEnum = null;
        }

        final String actorPattern = (actor == null || actor.isBlank()) ? null : actor.trim().toLowerCase(Locale.ROOT);
        final String keywordPattern = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase(Locale.ROOT);

        Sort sortObj = Sort.by(dir, sortField);
        if (!"maNhatKy".equals(sortField)) {
            sortObj = sortObj.and(Sort.by(dir, "maNhatKy"));
        }
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Specification<NhatKyHoatDong> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("thoiGian"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("thoiGian"), to));
            }
            if (hanhDongEnum != null) {
                predicates.add(cb.equal(root.get("hanhDong"), hanhDongEnum));
            }
            if (loaiDoiTuongEnum != null) {
                predicates.add(cb.equal(root.get("loaiDoiTuong"), loaiDoiTuongEnum));
            }
            if (actorPattern != null) {
                String escaped = "%" + escapeLike(actorPattern) + "%";
                Predicate maTkPred = cb.like(cb.lower(root.get("maTk")), escaped, '\\');
                Predicate tenPred = cb.like(cb.lower(root.get("tenNguoiThucHien")), escaped, '\\');
                predicates.add(cb.or(maTkPred, tenPred));
            }
            if (keywordPattern != null) {
                String escaped = "%" + escapeLike(keywordPattern) + "%";
                List<Predicate> orPreds = new ArrayList<>();
                orPreds.add(cb.like(cb.lower(root.get("maNhatKy")), escaped, '\\'));
                orPreds.add(cb.like(cb.lower(root.get("maTk")), escaped, '\\'));
                orPreds.add(cb.like(cb.lower(root.get("tenNguoiThucHien")), escaped, '\\'));
                orPreds.add(cb.like(cb.lower(root.get("maDoiTuong")), escaped, '\\'));
                orPreds.add(cb.like(cb.lower(root.get("moTa")), escaped, '\\'));
                orPreds.add(cb.like(cb.lower(root.get("requestPath")), escaped, '\\'));
                predicates.add(cb.or(orPreds.toArray(Predicate[]::new)));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<NhatKyHoatDong> pageResult = nhatKyHoatDongRepository.findAll(spec, pageable);
        List<NhatKyHoatDongDto> dtoList = pageResult.getContent().stream().map(this::toDto).toList();

        return new TrangDto<>(
                dtoList,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public NhatKyHoatDongChiTietDto get(String maNhatKy) {
        NhatKyHoatDong nhatKy = nhatKyHoatDongRepository.findById(maNhatKy)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký hoạt động: " + maNhatKy));
        return toChiTietDto(nhatKy);
    }

    private static String escapeLike(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
