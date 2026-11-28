package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AfterSalesCustomerConfirmationService {
    public static final String WAITING = "CHO_XAC_NHAN";
    private static final String NEEDS_SUPPORT = "CAN_HO_TRO";
    private static final String AUTO_DONE = "TU_DONG_HOAN_TAT";
    private final YeuCauDoiTraRepository returns;
    private final YeuCauHauMaiRepository afterSales;
    private final NhatKyHoatDongRepository auditRepo;
    private final ThongBaoService notifications;
    private final ObjectMapper mapper;

    public void request(YeuCauDoiTra request, String message) {
        request.setTrangThaiXacNhanKhach(WAITING);
        request.setHanXacNhanKhach(LocalDateTime.now().plusDays(3));
        request.setGhiChuXacNhanKhach(null);
        request.setNgayCapNhat(LocalDateTime.now());
        notifications.gui(request.getDonHang().getKhachHang().getMaKh(), message + " Vui lòng xác nhận tại trang Đổi trả trong 3 ngày.", ThongBaoService.linkYeuCau(request.getMaYeuCau()));
        log(request.getMaYeuCau(), "CHO_KHACH_XAC_NHAN", message);
    }

    public void request(YeuCauHauMai request, String message) {
        request.setTrangThaiXacNhanKhach(WAITING);
        request.setHanXacNhanKhach(LocalDateTime.now().plusDays(3));
        request.setGhiChuXacNhanKhach(null);
        request.setNgayCapNhat(LocalDateTime.now());
        afterHistory(request, message + " Vui lòng xác nhận tại trang Đổi trả trong 3 ngày.");
        notifications.gui(owner(request), message + " Vui lòng xác nhận tại trang Đổi trả trong 3 ngày.", ThongBaoService.linkYeuCau(request.getMaYeuCau()));
        log(request.getMaYeuCau(), "CHO_KHACH_XAC_NHAN", message);
    }

    public void confirm(String id, String customerId, boolean received, String note) {
        YeuCauDoiTra r = returns.findByIdForUpdate(id).orElse(null);
        if (r != null) {
            if (!r.getDonHang().getKhachHang().getMaKh().equals(customerId)) throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu");
            checkWaiting(r.getTrangThaiXacNhanKhach(), r.getHanXacNhanKhach()); applyReturn(r, received, note); return;
        }
        YeuCauHauMai h = afterSales.findByIdForUpdate(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!owner(h).equals(customerId)) throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu");
        checkWaiting(h.getTrangThaiXacNhanKhach(), h.getHanXacNhanKhach());
        if (received) {
            h.setTrangThaiXacNhanKhach("DA_XAC_NHAN"); h.setTrangThai(TrangThaiHauMai.HOAN_TAT);
            h.setKetQuaXuLy((h.getKetQuaXuLy() == null ? "" : h.getKetQuaXuLy() + " ") + "Khách xác nhận đã nhận kết quả xử lý.");
            afterHistory(h, "Khách xác nhận đã nhận kết quả xử lý.");
            notifications.gui(owner(h), "Yêu cầu " + id + " đã hoàn tất theo xác nhận của bạn.", ThongBaoService.linkYeuCau(id));
        } else {
            h.setTrangThaiXacNhanKhach(NEEDS_SUPPORT); h.setHanXacNhanKhach(null); h.setGhiChuXacNhanKhach(requiredNote(note));
            afterHistory(h, "Khách báo chưa nhận: " + h.getGhiChuXacNhanKhach());
            notifications.guiQuanTri("Khách báo chưa nhận kết quả hậu mãi " + id + ": " + h.getGhiChuXacNhanKhach());
        }
        h.setNgayCapNhat(LocalDateTime.now());
        log(id, received ? "KHACH_XAC_NHAN" : "KHACH_BAO_CHUA_NHAN", received ? "Khách đã xác nhận nhận hàng/kết quả" : requiredNote(note));
    }

    private void applyReturn(YeuCauDoiTra r, boolean received, String note) {
        if (received) {
            r.setTrangThaiXacNhanKhach("DA_XAC_NHAN"); r.setTrangThai(TrangThaiDoiTra.HOAN_TAT);
            r.setGhiChuXuLy("Khách xác nhận đã nhận máy/tiền hoàn.");
            notifications.gui(r.getDonHang().getKhachHang().getMaKh(), "Yêu cầu " + r.getMaYeuCau() + " đã hoàn tất theo xác nhận của bạn.", ThongBaoService.linkYeuCau(r.getMaYeuCau()));
        } else {
            r.setTrangThaiXacNhanKhach(NEEDS_SUPPORT); r.setHanXacNhanKhach(null); r.setGhiChuXacNhanKhach(requiredNote(note));
            notifications.guiQuanTri("Khách báo chưa nhận kết quả đổi trả " + r.getMaYeuCau() + ": " + r.getGhiChuXacNhanKhach());
        }
        r.setNgayCapNhat(LocalDateTime.now());
        log(r.getMaYeuCau(), received ? "KHACH_XAC_NHAN" : "KHACH_BAO_CHUA_NHAN", received ? "Khách đã xác nhận nhận hàng/tiền hoàn" : requiredNote(note));
    }

    private void checkWaiting(String state, LocalDateTime deadline) {
        if (!WAITING.equals(state) || deadline == null) throw new AppException(HttpStatus.CONFLICT, "Yêu cầu hiện không chờ xác nhận của khách");
        if (deadline.isBefore(LocalDateTime.now())) throw new AppException(HttpStatus.CONFLICT, "Đã hết hạn xác nhận; yêu cầu sẽ được hệ thống tự hoàn tất");
    }

    @Scheduled(fixedDelay = 60000)
    public void autoCompleteExpired() {
        LocalDateTime now = LocalDateTime.now();
        for (YeuCauDoiTra r : returns.findByTrangThaiXacNhanKhachAndHanXacNhanKhachBefore(WAITING, now)) {
            r.setTrangThai(TrangThaiDoiTra.HOAN_TAT); r.setTrangThaiXacNhanKhach(AUTO_DONE); r.setHanXacNhanKhach(null); r.setNgayCapNhat(now);
            r.setGhiChuXuLy("Tự động hoàn tất do khách không phản hồi trong 3 ngày.");
            notifications.gui(r.getDonHang().getKhachHang().getMaKh(), "Yêu cầu " + r.getMaYeuCau() + " đã tự hoàn tất do hết hạn xác nhận.", ThongBaoService.linkYeuCau(r.getMaYeuCau()));
            log(r.getMaYeuCau(), AUTO_DONE, r.getGhiChuXuLy());
        }
        for (YeuCauHauMai h : afterSales.findByTrangThaiXacNhanKhachAndHanXacNhanKhachBefore(WAITING, now)) {
            h.setTrangThai(TrangThaiHauMai.HOAN_TAT); h.setTrangThaiXacNhanKhach(AUTO_DONE); h.setHanXacNhanKhach(null); h.setNgayCapNhat(now);
            h.setKetQuaXuLy("Tự động hoàn tất do khách không phản hồi trong 3 ngày."); afterHistory(h, h.getKetQuaXuLy());
            notifications.gui(owner(h), "Yêu cầu " + h.getMaYeuCau() + " đã tự hoàn tất do hết hạn xác nhận.", ThongBaoService.linkYeuCau(h.getMaYeuCau()));
            log(h.getMaYeuCau(), AUTO_DONE, h.getKetQuaXuLy());
        }
    }

    private void afterHistory(YeuCauHauMai h, String note) {
        LichSuHauMai line = new LichSuHauMai(); line.setMaLichSu(IdGenerator.next("LSHM")); line.setYeuCau(h);
        line.setTrangThai(h.getTrangThai()); line.setGhiChu(note); line.setThoiGian(LocalDateTime.now()); h.getLichSu().add(line);
    }
    private String owner(YeuCauHauMai h) { return h.getKhachHang() != null ? h.getKhachHang().getMaKh() : h.getDonHang().getKhachHang().getMaKh(); }
    private String requiredNote(String note) { String value = note == null ? "" : note.trim(); if (value.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng ghi chú vấn đề"); return value.substring(0, Math.min(500, value.length())); }
    private void log(String id, String event, String message) {
        try {
            NhatKyHoatDong row = new NhatKyHoatDong(); row.setMaNhatKy(IdGenerator.next("NK")); row.setMaTk("SYSTEM"); row.setTenNguoiThucHien("PhoneNest System");
            row.setHanhDong(HanhDongAudit.CHANGE_RETURN_STATUS); row.setLoaiDoiTuong(LoaiDoiTuongAudit.RETURN); row.setMaDoiTuong(id);
            row.setMoTa("Cập nhật xác nhận hậu mãi " + id); row.setDuLieuSau(mapper.writeValueAsString(Map.of("suKien", event, "ghiNhan", message)));
            row.setRequestPath("system/after-sales-confirmation"); row.setThoiGian(LocalDateTime.now()); auditRepo.save(row);
        } catch (Exception e) { throw new IllegalStateException("Không thể ghi nhật ký xác nhận hậu mãi", e); }
    }
}
