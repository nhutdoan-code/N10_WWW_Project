package nhom10.phonenest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.AdminApprovalDto;
import nhom10.phonenest.dto.AdminUnblockDto;
import nhom10.phonenest.dto.PheDuyetChiTietDto;
import nhom10.phonenest.entity.NhatKyHoatDong;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.repository.NhatKyHoatDongRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static nhom10.phonenest.service.PheDuyetBanHangPolicy.*;

/**
 * Quy trinh phe duyet ban hang bien the (Giai doan 2B.7B).
 *
 * <pre>
 *   PENDING_REVIEW --approve(du lieu xac nhan khop + can cu)--> APPROVED
 *   APPROVED       --revoke(ly do)------------------------------> PENDING_REVIEW
 *   APPROVED       --sua gia/gia niem yet/RAM/ROM/mau-----------> PENDING_REVIEW (tu dong, SanPhamService)
 *   *              --block(lyDoKhoa + ly do)--------------------> BLOCKED
 *   BLOCKED        --unblock(du lieu xac nhan + can cu + dieu kien theo lyDoKhoa)--> PENDING_REVIEW
 *   BLOCKED        --approval PENDING/APPROVED------------------> TU CHOI (409)
 * </pre>
 * Moi thao tac khoa dong bien the (SELECT ... FOR UPDATE) va so sanh du lieu
 * Admin xac nhan voi du lieu HIEN TAI trong cung giao dich.
 */
@Service
@RequiredArgsConstructor
public class PheDuyetBanHangService {

    private final PhienBanSanPhamRepository pbRepo;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final NhatKyHoatDongRepository nhatKyRepo;
    private final ObjectMapper objectMapper;

    /**
     * Tach quyen 4 mat (2B.7C, mac dinh TAT vi hien chi co mot tai khoan Admin): khi bat,
     * Admin vua mo khoa mot bien the khong duoc tu phe duyet bien the do.
     */
    @Value("${app.approval.tach-quyen-mo-khoa-va-duyet:false}")
    private boolean tachQuyenMoKhoaVaDuyet;

    /** 2B.7D: phat su kien de dong bo vector store SAU khi commit (tuy chon, unit test co the khong co). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ doc (chi ADMIN)

    /** Danh sach bien the cho man hinh phe duyet, loc trong bo nho (vai tram dong). */
    @Transactional(readOnly = true)
    public List<PheDuyetChiTietDto> danhSach(TrangThaiPheDuyet trangThaiPheDuyet, TrangThaiSanPham trangThai,
                                            String maThuongHieu, String q, Boolean chiKhongDuocBan) {
        String qq = q == null ? "" : q.trim().toLowerCase();
        return pbRepo.findAll().stream()
                .filter(p -> trangThaiPheDuyet == null || p.getTrangThaiPheDuyet() == trangThaiPheDuyet)
                .filter(p -> trangThai == null || (trangThai == TrangThaiSanPham.DANG_KINH_DOANH) == dangKinhDoanh(p))
                .filter(p -> maThuongHieu == null || maThuongHieu.isBlank()
                        || (p.getSanPham() != null && p.getSanPham().getThuongHieu() != null
                        && maThuongHieu.equals(p.getSanPham().getThuongHieu().getMaThuongHieu())))
                .filter(p -> qq.isEmpty() || chua(p.getMaPhienBan(), qq) || chua(p.getSku(), qq)
                        || (p.getSanPham() != null && (chua(p.getSanPham().getTenMay(), qq)
                        || chua(p.getSanPham().getMaSanPham(), qq))))
                .filter(p -> !Boolean.TRUE.equals(chiKhongDuocBan) || !duocPhepBan(p))
                .sorted(Comparator.comparing((PhienBanSanPham p) -> p.getSanPham() == null ? "" : nzs(p.getSanPham().getTenMay()))
                        .thenComparing(PhienBanSanPham::getRam).thenComparing(PhienBanSanPham::getBoNho)
                        .thenComparing(p -> nzs(p.getMauSac())))
                .map(PheDuyetBanHangService::toDto)
                .toList();
    }

    /** Lich su phe duyet (audit VARIANT) cua mot bien the. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lichSu(String maPhienBan) {
        if (!pbRepo.existsById(maPhienBan)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản: " + maPhienBan);
        }
        List<Map<String, Object>> kq = new ArrayList<>();
        for (NhatKyHoatDong n : nhatKyRepo.findTop200ByLoaiDoiTuongAndMaDoiTuongOrderByThoiGianDescMaNhatKyDesc(
                LoaiDoiTuongAudit.VARIANT, maPhienBan)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("maNhatKy", n.getMaNhatKy());
            m.put("thoiGian", n.getThoiGian());
            m.put("maTk", n.getMaTk());
            m.put("tenNguoiThucHien", n.getTenNguoiThucHien());
            m.put("hanhDong", n.getHanhDong());
            m.put("moTa", n.getMoTa());
            m.put("truoc", docJson(n.getDuLieuTruoc()));
            m.put("sau", docJson(n.getDuLieuSau()));
            kq.add(m);
        }
        return kq;
    }

    @Transactional(readOnly = true)
    public PheDuyetChiTietDto chiTiet(String maPhienBan) {
        return toDto(pbRepo.findById(maPhienBan)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản: " + maPhienBan)));
    }

    @Transactional
    public PheDuyetChiTietDto capNhatTrangThai(String maPhienBan, AdminApprovalDto req) {
        if (req == null || req.trangThaiPheDuyet() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Trạng thái phê duyệt không được để trống");
        }
        PhienBanSanPham p = khoaDong(maPhienBan);
        kiemTraMaKiemTra(p, req.maKiemTra());
        Map<String, Object> truoc = anhChup(p);
        HanhDongAudit hanhDong = switch (req.trangThaiPheDuyet()) {
            case APPROVED -> pheDuyet(p, req);
            case PENDING_REVIEW -> thuHoi(p, req);
            case BLOCKED -> khoaBan(p, req);
        };
        pbRepo.save(p);
        ghiAudit(hanhDong, p, truoc);
        return toDto(p);
    }

    @Transactional
    public PheDuyetChiTietDto moKhoa(String maPhienBan, AdminUnblockDto req) {
        if (req == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Thiếu dữ liệu mở khóa");
        }
        PhienBanSanPham p = khoaDong(maPhienBan);
        kiemTraMaKiemTra(p, req.maKiemTra());
        if (p.getTrangThaiPheDuyet() != TrangThaiPheDuyet.BLOCKED) {
            throw new AppException(HttpStatus.CONFLICT, "Biến thể không ở trạng thái BLOCKED");
        }
        String lyDo = kiemTraVanBan(req.lyDo(), "Lý do mở khóa", LY_DO_MIN, LY_DO_MAX);
        String nguon = kiemTraVanBan(req.nguonXacMinh(), "Nguồn/căn cứ xác minh", NGUON_MIN, NGUON_MAX);
        if (!dangKinhDoanh(p)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Biến thể hoặc sản phẩm đang ngừng kinh doanh. Khôi phục kinh doanh trước khi mở khóa.");
        }
        doiChieuDuLieu(p, req.giaBanHienTai(), req.giaNiemYetHienTai(), req.ramHienTai(),
                req.boNhoHienTai(), req.mauSacHienTai());

        LyDoKhoaBan lyDoKhoa = p.getLyDoKhoa() == null ? LyDoKhoaBan.KHAC : p.getLyDoKhoa();
        if (lyDoKhoa == LyDoKhoaBan.GIA_SEED_CHUA_XAC_MINH
                && (p.getGiaBanKhiKhoa() == null || bangTien(p.getGiaBan(), p.getGiaBanKhiKhoa()))) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Biến thể bị khóa do giá seed chưa xác minh: giá bán hiện tại vẫn là giá tại thời điểm khóa. "
                            + "Cập nhật giá thị trường đã xác minh trước khi mở khóa.");
        }

        Map<String, Object> truoc = anhChup(p);
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.PENDING_REVIEW);
        p.setNguoiPheDuyet(SecurityUtil.currentUserId());
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet(cat("Mở khóa (" + lyDoKhoa + "): " + lyDo, LY_DO_MAX));
        p.setNguonXacMinh(nguon);
        xoaDuLieuPheDuyet(p, false);
        p.setLyDoKhoa(null);
        p.setGiaBanKhiKhoa(null);
        pbRepo.save(p);
        ghiAudit(HanhDongAudit.UNBLOCK_VARIANT, p, truoc);
        return toDto(p);
    }

    /**
     * Huy hieu luc phe duyet khi du lieu ban thay doi (goi tu SanPhamService.save).
     * APPROVED -> PENDING_REVIEW. BLOCKED giu nguyen (doi gia khong bao gio mo khoa).
     *
     * @return true neu trang thai bi ha cap
     */
    public static boolean huyHieuLucDoThayDoi(PhienBanSanPham p, String moTaThayDoi, String nguoiThucHien) {
        if (p.getTrangThaiPheDuyet() != TrangThaiPheDuyet.APPROVED) {
            return false;
        }
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.PENDING_REVIEW);
        p.setNguoiPheDuyet(nguoiThucHien);
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet(cat("Tự động hủy phê duyệt do thay đổi: " + moTaThayDoi, LY_DO_MAX));
        xoaDuLieuPheDuyet(p, true);
        return true;
    }

    // ------------------------------------------------------------------ chuyen trang thai

    private HanhDongAudit pheDuyet(PhienBanSanPham p, AdminApprovalDto req) {
        if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.BLOCKED) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Biến thể đang bị khóa bán (" + p.getLyDoKhoa() + "). Phải mở khóa qua /unblock và duyệt lại.");
        }
        if (p.getTrangThaiPheDuyet() != TrangThaiPheDuyet.PENDING_REVIEW) {
            throw new AppException(HttpStatus.CONFLICT, "Chỉ phê duyệt được biến thể đang PENDING_REVIEW");
        }
        if (!dangKinhDoanh(p)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể phê duyệt phiên bản của sản phẩm đã ngừng kinh doanh");
        }
        kiemTraTachQuyen(p);
        if (p.getGiaBan() == null || p.getGiaBan().signum() <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Giá bán không hợp lệ để phê duyệt");
        }
        if (p.getGiaNiemYet() != null && p.getGiaNiemYet().compareTo(p.getGiaBan()) < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Giá niêm yết không được nhỏ hơn giá bán");
        }
        String lyDo = kiemTraVanBan(req.lyDo(), "Lý do phê duyệt", LY_DO_MIN, LY_DO_MAX);
        String nguon = kiemTraVanBan(req.nguonXacMinh(), "Nguồn xác minh", NGUON_MIN, NGUON_MAX);
        if (req.giaPheDuyet() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Bắt buộc nhập giaPheDuyet (giá bán được xác nhận)");
        }
        doiChieuDuLieu(p, req.giaPheDuyet(), req.giaNiemYetPheDuyet(), req.ramPheDuyet(),
                req.boNhoPheDuyet(), req.mauSacPheDuyet());

        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.APPROVED);
        p.setNguoiPheDuyet(SecurityUtil.currentUserId());
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet(lyDo);
        p.setNguonXacMinh(nguon);
        p.setGiaPheDuyet(p.getGiaBan());
        p.setGiaNiemYetPheDuyet(p.getGiaNiemYet());
        p.setDauVanTayPheDuyet(dauVanTay(p));
        p.setLyDoKhoa(null);
        p.setGiaBanKhiKhoa(null);
        return HanhDongAudit.APPROVE_VARIANT;
    }

    private HanhDongAudit thuHoi(PhienBanSanPham p, AdminApprovalDto req) {
        if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.BLOCKED) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Biến thể đang BLOCKED: không được chuyển thẳng sang PENDING_REVIEW. Dùng quy trình mở khóa /unblock.");
        }
        if (p.getTrangThaiPheDuyet() != TrangThaiPheDuyet.APPROVED) {
            throw new AppException(HttpStatus.CONFLICT, "Biến thể đã ở trạng thái PENDING_REVIEW");
        }
        String lyDo = kiemTraVanBan(req.lyDo(), "Lý do thu hồi", LY_DO_MIN, LY_DO_MAX);
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.PENDING_REVIEW);
        p.setNguoiPheDuyet(SecurityUtil.currentUserId());
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet(lyDo);
        xoaDuLieuPheDuyet(p, true);
        return HanhDongAudit.REVOKE_VARIANT_APPROVAL;
    }

    private HanhDongAudit khoaBan(PhienBanSanPham p, AdminApprovalDto req) {
        if (req.lyDoKhoa() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Bắt buộc chọn lyDoKhoa khi khóa bán");
        }
        if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.BLOCKED) {
            throw new AppException(HttpStatus.CONFLICT, "Biến thể đã ở trạng thái BLOCKED");
        }
        String lyDo = kiemTraVanBan(req.lyDo(), "Lý do khóa bán", LY_DO_MIN, LY_DO_MAX);
        p.setTrangThaiPheDuyet(TrangThaiPheDuyet.BLOCKED);
        p.setNguoiPheDuyet(SecurityUtil.currentUserId());
        p.setNgayPheDuyet(LocalDateTime.now());
        p.setLyDoPheDuyet(lyDo);
        p.setLyDoKhoa(req.lyDoKhoa());
        p.setGiaBanKhiKhoa(p.getGiaBan());
        xoaDuLieuPheDuyet(p, true);
        return HanhDongAudit.BLOCK_VARIANT;
    }

    // ------------------------------------------------------------------ tien ich

    /** Ma kiem tra (tuy chon voi client API, UI Admin luon gui). Khac hien tai -> 409, khong ghi gi. */
    private static void kiemTraMaKiemTra(PhienBanSanPham p, String maKiemTra) {
        if (maKiemTra != null && !maKiemTra.equals(maKiemTraPheDuyet(p))) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Biến thể đã được thay đổi bởi thao tác khác kể từ khi bạn mở hộp thoại "
                            + "(giá, cấu hình, trạng thái kinh doanh hoặc phê duyệt). Tải lại dữ liệu trước khi thực hiện.",
                    "STALE_APPROVAL");
        }
    }

    private void kiemTraTachQuyen(PhienBanSanPham p) {
        if (!tachQuyenMoKhoaVaDuyet) {
            return;
        }
        String toi = SecurityUtil.currentUserId();
        nhatKyRepo.findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongInOrderByThoiGianDescMaNhatKyDesc(
                        LoaiDoiTuongAudit.VARIANT, p.getMaPhienBan(),
                        EnumSet.of(HanhDongAudit.UNBLOCK_VARIANT, HanhDongAudit.BLOCK_VARIANT, HanhDongAudit.APPROVE_VARIANT))
                .filter(n -> n.getHanhDong() == HanhDongAudit.UNBLOCK_VARIANT && toi != null && toi.equals(n.getMaTk()))
                .ifPresent(n -> {
                    throw new AppException(HttpStatus.FORBIDDEN,
                            "Tách quyền: Admin đã mở khóa biến thể này không được tự phê duyệt. Cần Admin khác phê duyệt.",
                            "SEPARATION_OF_DUTIES");
                });
    }

    private Object docJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return json;
        }
    }

    private static boolean chua(String s, String qq) {
        return s != null && s.toLowerCase().contains(qq);
    }

    private static String nzs(String s) {
        return s == null ? "" : s;
    }

    private PhienBanSanPham khoaDong(String maPhienBan) {
        return pbRepo.findByIdForUpdate(maPhienBan)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản: " + maPhienBan));
    }

    /** So khop du lieu Admin xac nhan voi du lieu hien tai (da khoa dong). */
    private static void doiChieuDuLieu(PhienBanSanPham p, BigDecimal gia, BigDecimal giaNiemYet,
                                       Integer ram, Integer boNho, String mau) {
        List<String> thieu = new ArrayList<>();
        if (gia == null) thieu.add("giá bán");
        if (p.getGiaNiemYet() != null && giaNiemYet == null) thieu.add("giá niêm yết");
        if (ram == null) thieu.add("RAM");
        if (boNho == null) thieu.add("ROM");
        if (mau == null || mau.isBlank()) thieu.add("màu sắc");
        if (!thieu.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Bắt buộc xác nhận lại dữ liệu hiện tại của biến thể: " + String.join(", ", thieu));
        }
        List<String> lech = new ArrayList<>();
        if (!bangTien(gia, p.getGiaBan())) lech.add("giá bán hiện tại là " + p.getGiaBan());
        if (!bangTien(giaNiemYet, p.getGiaNiemYet())) lech.add("giá niêm yết hiện tại là " + p.getGiaNiemYet());
        if (ram != p.getRam()) lech.add("RAM hiện tại là " + p.getRam());
        if (boNho != p.getBoNho()) lech.add("ROM hiện tại là " + p.getBoNho());
        if (!chuanHoaMau(mau).equals(chuanHoaMau(p.getMauSac()))) lech.add("màu hiện tại là " + p.getMauSac());
        if (!lech.isEmpty()) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Dữ liệu biến thể đã thay đổi so với dữ liệu được xác nhận (" + String.join("; ", lech)
                            + "). Tải lại và xác minh lại trước khi lưu.");
        }
    }

    private static void xoaDuLieuPheDuyet(PhienBanSanPham p, boolean xoaNguon) {
        p.setGiaPheDuyet(null);
        p.setGiaNiemYetPheDuyet(null);
        p.setDauVanTayPheDuyet(null);
        if (xoaNguon) {
            p.setNguonXacMinh(null);
        }
    }

    private void ghiAudit(HanhDongAudit hanhDong, PhienBanSanPham p, Map<String, Object> truoc) {
        if (eventPublisher != null && p.getSanPham() != null) {
            eventPublisher.publishEvent(new nhom10.phonenest.event.BienTheThayDoiEvent(p.getSanPham().getMaSanPham()));
        }
        nhatKyHoatDongService.ghi(hanhDong, LoaiDoiTuongAudit.VARIANT, p.getMaPhienBan(), truoc, anhChupSau(p));
    }

    /**
     * Anh chup "sau" cho audit VARIANT: anhChup + khoi "boiCanh" (chi co o ban ghi sau).
     * Audit chi luu cac truong THAY DOI; boiCanh dam bao lich su luon co du gia, gia niem yet,
     * cau hinh, ton kho va trang thai tai thoi diem thao tac (2B.7C).
     */
    static Map<String, Object> anhChupSau(PhienBanSanPham p) {
        Map<String, Object> m = anhChup(p);
        Map<String, Object> bc = new LinkedHashMap<>();
        bc.put("ram", p.getRam());
        bc.put("boNho", p.getBoNho());
        bc.put("mauSac", p.getMauSac());
        bc.put("giaBan", p.getGiaBan());
        bc.put("giaNiemYet", p.getGiaNiemYet());
        bc.put("soLuongTon", p.getSoLuongTon());
        bc.put("trangThai", p.getTrangThai() != null ? p.getTrangThai().name() : null);
        bc.put("trangThaiPheDuyet", p.getTrangThaiPheDuyet() != null ? p.getTrangThaiPheDuyet().name() : null);
        bc.put("lyDoKhoa", p.getLyDoKhoa() != null ? p.getLyDoKhoa().name() : null);
        m.put("boiCanh", bc);
        return m;
    }

    /** Anh chup du lieu phe duyet cho audit (khong chua thong tin nhay cam). */
    static Map<String, Object> anhChup(PhienBanSanPham p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maPhienBan", p.getMaPhienBan());
        m.put("maSanPham", p.getSanPham() != null ? p.getSanPham().getMaSanPham() : null);
        m.put("trangThaiPheDuyet", p.getTrangThaiPheDuyet() != null ? p.getTrangThaiPheDuyet().name() : null);
        m.put("lyDoKhoa", p.getLyDoKhoa() != null ? p.getLyDoKhoa().name() : null);
        m.put("giaBan", p.getGiaBan());
        m.put("giaNiemYet", p.getGiaNiemYet());
        m.put("ram", p.getRam());
        m.put("boNho", p.getBoNho());
        m.put("mauSac", p.getMauSac());
        m.put("giaPheDuyet", p.getGiaPheDuyet());
        m.put("giaNiemYetPheDuyet", p.getGiaNiemYetPheDuyet());
        m.put("nguonXacMinh", p.getNguonXacMinh());
        m.put("dauVanTayPheDuyet", p.getDauVanTayPheDuyet());
        m.put("giaBanKhiKhoa", p.getGiaBanKhiKhoa());
        m.put("lyDoPheDuyet", p.getLyDoPheDuyet());
        m.put("nguoiPheDuyet", p.getNguoiPheDuyet());
        return m;
    }

    private static PheDuyetChiTietDto toDto(PhienBanSanPham p) {
        return new PheDuyetChiTietDto(
                p.getMaPhienBan(),
                p.getSanPham() != null ? p.getSanPham().getMaSanPham() : null,
                p.getSanPham() != null ? p.getSanPham().getTenMay() : null,
                p.getSanPham() != null && p.getSanPham().getThuongHieu() != null
                        ? p.getSanPham().getThuongHieu().getMaThuongHieu() : null,
                p.getSanPham() != null && p.getSanPham().getThuongHieu() != null
                        ? p.getSanPham().getThuongHieu().getTenThuongHieu() : null,
                p.getSku(),
                p.getRam(), p.getBoNho(), p.getMauSac(), p.getGiaBan(), p.getGiaNiemYet(), p.getSoLuongTon(),
                p.getTrangThai(), p.getSanPham() != null ? p.getSanPham().getTrangThai() : null,
                p.getTrangThaiPheDuyet(),
                duocPhepBan(p), pheDuyetConHieuLuc(p),
                p.getNguoiPheDuyet(), p.getNgayPheDuyet(), p.getLyDoPheDuyet(),
                p.getGiaPheDuyet(), p.getGiaNiemYetPheDuyet(), p.getNguonXacMinh(),
                p.getLyDoKhoa(), p.getGiaBanKhiKhoa(),
                p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED && !pheDuyetConHieuLuc(p),
                maKiemTraPheDuyet(p));
    }

    private static String cat(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
