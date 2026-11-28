package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.AfterSalesPolicy;
import nhom10.phonenest.util.IdGenerator;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AfterSalesAdminWorkflow {
    private final YeuCauDoiTraRepository returns;
    private final YeuCauHauMaiRepository after;
    private final NhatKyHoatDongRepository logs;
    private final NhatKyHoatDongService audit;
    private final DoiTraService returnService;
    private final HauMaiService afterService;
    private final ThongBaoService notifications;
    private final TaiKhoanRepository accounts;
    private final QuanTriVienRepository admins;
    private final PhanCongHauMaiRepository assignments;
    private final ThanhToanRepository payments;
    public static final Set<String> REJECT_REASONS = Set.of("Quá hạn chính sách", "Không đủ điều kiện", "Minh chứng không hợp lệ", "Sản phẩm không khớp", "Khác");
    public record Action(String action, String note, String reason, String department, String status, String ketQuaXuLy) {
        public Action(String action, String note, String reason, String department, String status) {
            this(action, note, reason, department, status, null);
        }
    }
    public record AssignmentRequest(String maNguoiPhuTrach, LocalDateTime hanXuLy, String ghiChu) {}
    public record Assignment(String maNguoiPhuTrach, String tenNguoiPhuTrach, LocalDateTime hanXuLy) {}
    public record Detail(List<NhatKyHoatDongChiTietDto> timeline, Set<String> steps, String phone,
                         LocalDateTime created, LocalDateTime delivered, LocalDateTime confirmed,
                         LocalDateTime deadline, Boolean eligibleAtSubmission, String voucher,
                         java.math.BigDecimal discount, String orderStatus, String payment, java.math.BigDecimal gross, java.math.BigDecimal allocatedDiscount,
                         Assignment assignment, boolean codPaid) {}
    private AppException bad(String message) { return new AppException(HttpStatus.BAD_REQUEST, message); }
    private AppException missing() { return new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"); }
    private boolean isReturn(String source) {
        if (!Set.of("return", "after").contains(source)) throw bad("Loại yêu cầu không hợp lệ");
        return source.equals("return");
    }
    private List<NhatKyHoatDongChiTietDto> history(String id) {
        return logs.findByLoaiDoiTuongAndMaDoiTuongOrderByThoiGianDescMaNhatKyDesc(LoaiDoiTuongAudit.RETURN, id)
                .stream().map(audit::toChiTietDto).toList();
    }
    private Set<String> steps(List<NhatKyHoatDongChiTietDto> rows) {
        Set<String> result = new HashSet<>();
        for (var row : rows) {
            JsonNode n = row.duLieuSau();
            if (n != null && n.has("suKien")) result.add(n.path("suKien").asText());
        }
        return result;
    }
    @Transactional(readOnly = true)
    public List<AdminAfterSalesAssigneeDto> assignees() {
        var allAdmins = admins.findAll();
        var activeIds = java.util.stream.StreamSupport.stream(
                        accounts.findAllById(allAdmins.stream().map(QuanTriVien::getMaQtv).toList()).spliterator(), false)
                .filter(TaiKhoan::isTrangThaiHoatDong).map(TaiKhoan::getMaTk).collect(java.util.stream.Collectors.toSet());
        return allAdmins.stream().filter(a -> activeIds.contains(a.getMaQtv()))
                .map(a -> new AdminAfterSalesAssigneeDto(a.getMaQtv(), a.getHoTen()))
                .sorted(Comparator.comparing(AdminAfterSalesAssigneeDto::hoTen,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
    }
    @Transactional(readOnly = true)
    public Assignment assignment(String source, String id) {
        isReturn(source);
        return assignments.findByNguonAndMaYeuCau(source, id)
                .map(a -> new Assignment(a.getMaNguoiPhuTrach(), a.getTenNguoiPhuTrach(), a.getHanXuLy()))
                .orElse(null);
    }
    public Assignment assign(String source, String id, AssignmentRequest request) {
        boolean ret = isReturn(source);
        if (request == null || request.maNguoiPhuTrach() == null || request.maNguoiPhuTrach().isBlank()) throw bad("Ch\u1ecdn ng\u01b0\u1eddi ph\u1ee5 tr\u00e1ch");
        if (request.hanXuLy() == null || !request.hanXuLy().isAfter(LocalDateTime.now())) throw bad("H\u1ea1n x\u1eed l\u00fd ph\u1ea3i n\u1eb1m trong t\u01b0\u01a1ng lai");
        String note = Objects.toString(request.ghiChu(), "").trim();
        if (note.isBlank() || note.length() > 500) throw bad("Nh\u1eadp l\u00fd do giao vi\u1ec7c (t\u1ed1i \u0111a 500 k\u00fd t\u1ef1)");
        if (ret) {
            var row = returns.findByIdForUpdate(id).orElseThrow(this::missing);
            if (Set.of(TrangThaiDoiTra.HOAN_TAT, TrangThaiDoiTra.TU_CHOI).contains(row.getTrangThai())) throw bad("Kh\u00f4ng th\u1ec3 giao vi\u1ec7c cho y\u00eau c\u1ea7u \u0111\u00e3 \u0111\u00f3ng");
        } else {
            var row = after.findByIdForUpdate(id).orElseThrow(this::missing);
            if (Set.of(TrangThaiHauMai.HOAN_TAT, TrangThaiHauMai.TU_CHOI).contains(row.getTrangThai())) throw bad("Kh\u00f4ng th\u1ec3 giao vi\u1ec7c cho y\u00eau c\u1ea7u \u0111\u00e3 \u0111\u00f3ng");
        }
        var admin = admins.findById(request.maNguoiPhuTrach().trim()).orElseThrow(() -> bad("T\u00e0i kho\u1ea3n kh\u00f4ng ph\u1ea3i qu\u1ea3n tr\u1ecb vi\u00ean"));
        if (!accounts.findById(admin.getMaQtv()).map(TaiKhoan::isTrangThaiHoatDong).orElse(false)) throw bad("T\u00e0i kho\u1ea3n qu\u1ea3n tr\u1ecb vi\u00ean \u0111\u00e3 b\u1ecb kh\u00f3a");
        var current = assignments.findByNguonAndMaYeuCau(source, id).orElseGet(() -> {
            var created = new PhanCongHauMai();
            created.setMaPhanCong(IdGenerator.next("PC"));
            created.setNguon(source);
            created.setMaYeuCau(id);
            return created;
        });
        Map<String, Object> before = new LinkedHashMap<>();
        if (current.getMaNguoiPhuTrach() != null) {
            before.put("maNguoiPhuTrach", current.getMaNguoiPhuTrach());
            before.put("tenNguoiPhuTrach", current.getTenNguoiPhuTrach());
            before.put("hanXuLy", current.getHanXuLy());
        }
        current.setMaNguoiPhuTrach(admin.getMaQtv());
        current.setTenNguoiPhuTrach(admin.getHoTen());
        current.setHanXuLy(request.hanXuLy());
        current.setCapNhatBoi(SecurityUtil.currentUserId());
        current.setCapNhatLuc(LocalDateTime.now());
        assignments.save(current);
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("suKien", "ASSIGNMENT");
        afterData.put("maNguoiPhuTrach", admin.getMaQtv());
        afterData.put("tenNguoiPhuTrach", admin.getHoTen());
        afterData.put("hanXuLy", request.hanXuLy());
        afterData.put("ghiNhan", note);
        record(id, before, afterData);
        return new Assignment(current.getMaNguoiPhuTrach(), current.getTenNguoiPhuTrach(), current.getHanXuLy());
    }
    @Transactional(readOnly = true)
    public Object requestDetail(String source, String id) {
        return isReturn(source) ? returnService.chiTietAdmin(id) : afterService.chiTietAdmin(id);
    }
    @Transactional(readOnly = true)
    public Detail detail(String source, String id) {
        DonHang order; String owner; LocalDateTime created; boolean warranty; boolean complaint;
        java.math.BigDecimal gross = null, allocated = null;
        if (isReturn(source)) {
            var y = returns.findById(id).orElseThrow(this::missing);
            order = y.getDonHang(); owner = order.getKhachHang().getMaKh(); created = y.getNgayTao(); warranty = false; complaint = false;
            gross = java.math.BigDecimal.ZERO; allocated = java.math.BigDecimal.ZERO;
            var subtotal = Objects.requireNonNullElse(order.getTamTinh(), java.math.BigDecimal.ZERO);
            var discount = Objects.requireNonNullElse(order.getGiamGia(), java.math.BigDecimal.ZERO);
            for (var item : y.getChiTiet()) {
                if (item.getLoaiYeuCau() != LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN) continue;
                var price = item.getChiTietDonHang().getDonGia().multiply(java.math.BigDecimal.valueOf(item.getSoLuong()));
                gross = gross.add(price);
                if (subtotal.signum() > 0) allocated = allocated.add(discount.multiply(price).divide(subtotal, 0, java.math.RoundingMode.FLOOR).min(price));
            }
        } else {
            var y = after.findById(id).orElseThrow(this::missing);
            order = y.getDonHang(); owner = y.getKhachHang() != null ? y.getKhachHang().getMaKh() : order.getKhachHang().getMaKh();
            created = y.getNgayTao(); warranty = y.getLoai() == LoaiHauMai.BAO_HANH; complaint = !warranty;
        }
        var timeline = history(id);
        String phone = order == null ? null : order.getSoDienThoaiNhan();
        if (phone == null || phone.isBlank()) phone = accounts.findById(owner).map(TaiKhoan::getSoDienThoai).orElse(null);
        var delivered = order == null ? null : order.getNgayGiao();
        var confirmed = order == null ? null : order.getNgayKhachXacNhan();
        var deadline = complaint ? null : AfterSalesPolicy.end(delivered, confirmed, warranty);
        Boolean eligible = order == null || complaint || created == null ? null : AfterSalesPolicy.eligible(order.getTrangThai(), delivered, confirmed, warranty, created);
        var payment = order == null ? null : nhom10.phonenest.util.OrderPaymentPolicy.effective(
                payments.findByDonHangMaDonHangOrderByNgayTaoDesc(order.getMaDonHang()));
        return new Detail(timeline, steps(timeline), phone, created, delivered, confirmed, deadline, eligible,
                order == null || order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaKm(),
                order == null ? null : order.getGiamGia(), order == null ? null : order.getTrangThai().name(),
                payment == null || payment.getPhuongThuc() == null ? null : payment.getPhuongThuc().name(), gross, allocated,
                assignment(source, id), order != null && nhom10.phonenest.util.OrderPaymentPolicy.codPaid(payment, order.getTongThanhToan()));
    }
    public void execute(String source, String id, Action action) {
        if (action == null || action.action() == null) throw bad("Chọn thao tác");
        boolean ret = isReturn(source);
        YeuCauDoiTra r = ret ? returns.findByIdForUpdate(id).orElseThrow(this::missing) : null;
        YeuCauHauMai h = ret ? null : after.findByIdForUpdate(id).orElseThrow(this::missing);
        String status = ret ? r.getTrangThai().name() : h.getTrangThai().name();
        if (Set.of("HOAN_TAT", "TU_CHOI").contains(status)) throw bad("Yêu cầu đã đóng");
        boolean warranty = !ret && h.getLoai() == LoaiHauMai.BAO_HANH;
        String note = Objects.toString(action.note(), "").trim();
        if (note.length() > 500) throw bad("Phản hồi tối đa 500 ký tự");
        String event = action.action();
        var done = steps(history(id));
        if ("STATUS".equals(event)) {
            String next = Objects.toString(action.status(), "");
            if ("TU_CHOI".equals(next)) {
                if (!REJECT_REASONS.contains(Objects.toString(action.reason(), "")) || note.isBlank()) throw bad("Chọn lý do từ chối và nhập phản hồi riêng");
                note = "Từ chối — " + action.reason() + ": " + note;
                if (note.length() > 500) throw bad("Phản hồi quá dài");
            }
            if (warranty && "HOAN_TAT".equals(next) && !done.contains("WARRANTY_HANDOVER")) throw bad("Cần xác nhận đã trả máy cho khách trước khi hoàn tất");
            if (ret && "HOAN_TAT".equals(next) && r.getChiTiet().stream().anyMatch(c -> c.getLoaiYeuCau() == LoaiYeuCauDoiTra.DOI_HANG) && !done.contains("EXCHANGE_HANDOVER")) throw bad("Cần xác nhận đã giao máy đổi trước khi hoàn tất");
            if (ret) {
                if (!Set.of("DA_DUYET", "DANG_XU_LY", "HOAN_TAT", "TU_CHOI").contains(next)) throw bad("Trạng thái không hợp lệ");
                returnService.capNhat(id, TrangThaiDoiTra.valueOf(next), note);
            } else {
                if (!Set.of("DANG_KIEM_TRA", "DANG_XAC_MINH", "DANG_XU_LY", "HOAN_TAT", "TU_CHOI").contains(next)) throw bad("Trạng thái không hợp lệ");
                String outcome = Objects.toString(action.ketQuaXuLy(), "").trim();
                afterService.xuLy(id, new HauMaiDto.XuLyRequest(
                        TrangThaiHauMai.valueOf(next), note, outcome.isBlank() ? null : outcome));
            }
            if (!ret) record(id, Map.of("suKien", "STATUS", "trangThai", next, "phanHoi", note));
            return;
        }
        String customer = ret ? r.getDonHang().getKhachHang().getMaKh() : h.getKhachHang() != null ? h.getKhachHang().getMaKh() : h.getDonHang().getKhachHang().getMaKh();
        boolean send = false;
        switch (event) {
            case "TRIAGE" -> { if (!ret || !"CHO_DUYET".equals(status) || note.isBlank()) throw bad("Chỉ tiếp nhận yêu cầu đang chờ duyệt và cần ghi phản hồi"); send = true; }
            case "REQUEST_INFO", "REPLY" -> { if (note.isBlank()) throw bad("Vui lòng nhập phản hồi gửi khách"); send = true; }
            case "TRANSFER" -> { if (!Set.of("Kỹ thuật", "Kho", "Kế toán", "Chăm sóc khách hàng").contains(Objects.toString(action.department(), "")) || note.isBlank()) throw bad("Chọn bộ phận và ghi lý do điều chuyển"); }
            case "RECEIVE_CHECK" -> {
                if (!ret || note.isBlank()) throw bad("Cần ghi kết quả kiểm tra máy");
                boolean exchange = r.getChiTiet().stream().anyMatch(c -> c.getLoaiYeuCau() == LoaiYeuCauDoiTra.DOI_HANG);
                if (exchange && "CHO_DUYET".equals(status)) returnService.inspectExchange(id);
                else if (exchange || !Set.of("DA_DUYET", "DANG_XU_LY").contains(status)) throw bad("Không thể kiểm tra ở trạng thái này");
            }
            case "EXCHANGE_PAYMENT" -> { if (!ret) throw bad("Chỉ áp dụng đổi máy"); returnService.recordExchangePayment(id, note); }
            case "EXCHANGE_HANDOVER" -> { if (!ret || !"DANG_XU_LY".equals(status) || r.getChiTiet().isEmpty() || r.getChiTiet().stream().anyMatch(c -> !c.isDaNhapKho() || c.getLoaiYeuCau() != LoaiYeuCauDoiTra.DOI_HANG) || note.isBlank()) throw bad("Cần xử lý máy cũ và ghi thông tin máy đã giao đổi"); returnService.handoverExchange(id); }
            case "WARRANTY_RECEIVE" -> { if (!warranty || !"DANG_XU_LY".equals(status) || note.isBlank()) throw bad("Cần tiếp nhận bảo hành và ghi nhận tình trạng máy"); }
            case "WARRANTY_REPAIR" -> { if (!warranty || !"DANG_XU_LY".equals(status) || !done.contains("WARRANTY_RECEIVE") || note.isBlank()) throw bad("Cần nhận máy và ghi thông tin sửa chữa"); }
            case "WARRANTY_HANDOVER" -> { if (!warranty || !"DANG_XU_LY".equals(status) || !done.contains("WARRANTY_REPAIR") || note.isBlank()) throw bad("Cần xử lý máy và ghi nhận trả máy cho khách"); }
            default -> throw bad("Thao tác không hợp lệ");
        }
        if (!Set.of("REQUEST_INFO", "REPLY", "TRANSFER").contains(event) && done.contains(event)) throw bad("Bước này đã được ghi nhận");
        LocalDateTime now = LocalDateTime.now();
        if (send) {
            String message = "REQUEST_INFO".equals(event) ? "Cần bổ sung thông tin/ảnh: " + note : note;
            if (message.length() > 500) throw bad("Phản hồi quá dài");
            if (ret) r.setGhiChuXuLy(message);
            else {
                h.setGhiChuXuLy(message);
                LichSuHauMai line = new LichSuHauMai(); line.setMaLichSu(IdGenerator.next("LSHM")); line.setYeuCau(h);
                line.setTrangThai(h.getTrangThai()); line.setGhiChu(message); line.setThoiGian(now); h.getLichSu().add(line);
            }
            notifications.gui(customer, message, ThongBaoService.linkYeuCau(id));
        }
        if (ret) r.setNgayCapNhat(now); else h.setNgayCapNhat(now);
        Map<String,Object> data = new LinkedHashMap<>(); data.put("suKien", event);
        data.put(send ? "phanHoi" : "ghiNhan", note);
        if ("TRANSFER".equals(event)) data.put("boPhan", action.department());
        record(id, data);
    }
    public DoiTraDto restock(String id, String admin) { return restock(id, admin, false); }
    public DoiTraDto restock(String id, String admin, boolean sellable) {
        var y = returns.findByIdForUpdate(id).orElseThrow(this::missing);
        if (!steps(history(id)).contains("RECEIVE_CHECK")) throw bad("Cần xác nhận đã nhận và kiểm tra máy trước khi nhập kho");
        return returnService.xacNhanNhapKho(id, admin, sellable);
    }
    public void checkCompletion(String source, String id) {
        if (isReturn(source)) {
            var y = returns.findByIdForUpdate(id).orElseThrow(this::missing);
            if (y.getChiTiet().stream().anyMatch(c -> c.getLoaiYeuCau() == LoaiYeuCauDoiTra.DOI_HANG) && !steps(history(id)).contains("EXCHANGE_HANDOVER"))
                throw bad("Cần ghi nhận đã giao máy đổi");
        } else {
            var y = after.findByIdForUpdate(id).orElseThrow(this::missing);
            if (y.getLoai() == LoaiHauMai.BAO_HANH && !steps(history(id)).contains("WARRANTY_HANDOVER")) throw bad("Cần xác nhận đã trả máy cho khách");
        }
    }
    private void record(String id, Map<String, ?> data) {
        record(id, Map.of(), data);
    }
    private void record(String id, Map<String, ?> before, Map<String, ?> data) {
        audit.ghi(HanhDongAudit.CHANGE_RETURN_STATUS, LoaiDoiTuongAudit.RETURN, id, before, data);
    }
}
