package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.IdGenerator;
import nhom10.phonenest.util.ExchangePolicy;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DoiTraService {


    private final YeuCauDoiTraRepository repo;
    private final DonHangRepository donRepo;
    private final ThongBaoService thongBao;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final KhoService khoService;
    private final PhienBanSanPhamRepository phienBanRepo;
    private final ThanhToanRepository thanhToanRepo;
    private final MomoPaymentService momoPaymentService;
    private final VnPayPaymentService vnPayPaymentService;

    public record ExchangeOption(String id, String label, BigDecimal price, int stock) {}
    private boolean exchangeAvailable(PhienBanSanPham p, int quantity) {
        return PheDuyetBanHangPolicy.duocPhepBan(p) && p.getGiaBan() != null && p.getGiaBan().signum() > 0 && p.getSoLuongTon() >= quantity;
    }
    private ExchangeOption exchangeOption(PhienBanSanPham p) {
        return new ExchangeOption(p.getMaPhienBan(), p.getSanPham().getTenMay() + " · " + p.getRam() + "GB RAM / " + p.getBoNho() + "GB · " + p.getMauSac(), p.getGiaBan(), p.getSoLuongTon());
    }
    @Transactional(readOnly = true)
    public List<ExchangeOption> exchangeOptions() {
        return phienBanRepo.findAll().stream().filter(p -> exchangeAvailable(p, 1)).map(this::exchangeOption)
                .sorted(Comparator.comparing(ExchangeOption::label)).toList();
    }
    public DoiTraDto taoTuForm(String maKh, nhom10.phonenest.dto.AfterSalesInput input, LoaiYeuCauDoiTra type, LuuTruAnhService storage) {
        DonHang order = donRepo.findByIdForUpdate(input.maDonHang()).filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        nhom10.phonenest.util.AfterSalesPolicy.require(order, false);
        if (input.chiTiet() == null) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng cập nhật biểu mẫu đổi trả mới");
        if (input.maPhienBan() == null || input.maPhienBan().isBlank() || input.soLuong() == null || input.soLuong() < 1)
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn sản phẩm và số lượng hợp lệ");
        var details = new HashMap<>(input.chiTiet());
        if (type == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN) {
            var payment = nhom10.phonenest.util.OrderPaymentPolicy.effective(
                    thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc(order.getMaDonHang()));
            if (payment == null || payment.getPhuongThuc() == null)
                throw new AppException(HttpStatus.CONFLICT, "Chưa có dữ liệu thanh toán; cần đối soát trước khi gửi yêu cầu hoàn tiền");
            if (!nhom10.phonenest.util.ReturnRefundPolicy.customerAllowed(payment.getPhuongThuc(), details.get("refund")))
                throw new AppException(HttpStatus.BAD_REQUEST, "Hình thức hoàn tiền không phù hợp với giao dịch gốc");
            if (!"Chuyển khoản".equals(details.get("refund"))) {
                details.remove("bank"); details.remove("account"); details.remove("holder");
            }
        }
        ExchangePlan plan = null;
        if (type == LoaiYeuCauDoiTra.DOI_HANG) {
            int quantity = input.soLuong();
            var line = order.getChiTiet().stream().filter(c -> c.getPhienBan().getMaPhienBan().equals(input.maPhienBan())).findFirst()
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc đơn hàng"));
            if (quantity > line.getSoLuong()) throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng đổi không hợp lệ");
            String reason = details.get("reason");
            if (!ExchangePolicy.same(reason) && !ExchangePolicy.upgrade(reason))
                throw new AppException(HttpStatus.BAD_REQUEST, "Lý do này không hỗ trợ đổi máy; vui lòng chọn trả hàng / hoàn tiền");
            String targetId = ExchangePolicy.same(reason) ? input.maPhienBan() : details.get("targetId");
            if (targetId == null || targetId.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn phiên bản muốn đổi");
            if (ExchangePolicy.same(reason) && details.get("targetId") != null && !details.get("targetId").isBlank() && !targetId.equals(details.get("targetId")))
                throw new AppException(HttpStatus.BAD_REQUEST, "Máy lỗi hoặc giao sai hàng chỉ được đổi đúng phiên bản đã mua");
            var target = phienBanRepo.findById(targetId).filter(p -> exchangeAvailable(p, quantity))
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Phiên bản muốn đổi không còn đủ hàng hoặc không được bán"));
            var net = ExchangePolicy.net(line.getDonGia(), quantity, order.getTamTinh(), order.getGiamGia());
            var delta = exchangeDifference(reason, net, target.getGiaBan().multiply(BigDecimal.valueOf(quantity)));
            plan = new ExchangePlan(reason, targetId, net, delta);
            var option = exchangeOption(target);
            details.put("target", option.label() + " [" + option.id() + "]");
        }
        var normalized = new nhom10.phonenest.dto.AfterSalesInput(input.maDonHang(), input.maPhienBan(), input.soLuong(), input.loai(), type, input.lyDo(), input.hinhAnhMinhChung(), input.anhMinhChung(), details);
        return tao(maKh, new DoiTraDto.Request(input.maDonHang(), normalized.description(type.name()), normalized.images(type.name(), maKh, storage), type, input.maPhienBan(), input.soLuong()), plan);
    }

    private record ExchangePlan(String reason, String targetId, BigDecimal oldNet, BigDecimal delta) {}
    private BigDecimal exchangeDifference(String reason, BigDecimal oldNet, BigDecimal total) {
        try { return ExchangePolicy.difference(reason, oldNet, total); }
        catch (IllegalArgumentException e) { throw new AppException(HttpStatus.CONFLICT, e.getMessage()); }
    }
    public DoiTraDto tao(String maKh, DoiTraDto.Request r) {
        return tao(maKh, r, null);
    }
    private DoiTraDto tao(String maKh, DoiTraDto.Request r, ExchangePlan plan) {
        // 2B.7E: khoa dong don hang (serialize cac yeu cau dong thoi); kiem tra trung tung san pham
        // ben duoi chay trong khoa nay -> mot san pham khong the duoc hoan tien nhieu lan.
        DonHang don = donRepo.findByIdForUpdate(r.maDonHang())
                .filter(d -> d.getKhachHang().getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
        nhom10.phonenest.util.AfterSalesPolicy.require(don, false);
        String lyDo = r.lyDo().trim();
        if (lyDo.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do đổi trả");

        // Payload cũ không có maPhienBan: giữ tương thích, yêu cầu toàn bộ đơn hàng.
        List<ChiTietDonHang> mucYeuCau;
        if (r.maPhienBan() == null || r.maPhienBan().isBlank()) {
            mucYeuCau = don.getChiTiet();
        } else {
            mucYeuCau = don.getChiTiet().stream()
                    .filter(c -> c.getPhienBan().getMaPhienBan().equals(r.maPhienBan()))
                    .toList();
            if (mucYeuCau.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc đơn hàng");
            }
        }
        if (mucYeuCau.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng không có sản phẩm");
        if (r.soLuong() != null && mucYeuCau.size() != 1) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn một sản phẩm khi nhập số lượng");
        }
        List<YeuCauDoiTra> daCo = repo.findByDonHangMaDonHangOrderByNgayTaoDesc(don.getMaDonHang());
        for (ChiTietDonHang c : mucYeuCau) {
            boolean trung = daCo.stream().filter(y -> y.getTrangThai() != TrangThaiDoiTra.TU_CHOI)
                    .flatMap(y -> y.getChiTiet().stream())
                    .anyMatch(ct -> ct.getChiTietDonHang().getMaChiTiet().equals(c.getMaChiTiet()));
            if (trung) {
                throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu đổi trả; vui lòng theo dõi yêu cầu cũ");
            }
        }

        YeuCauDoiTra yeuCau = new YeuCauDoiTra();
        yeuCau.setMaYeuCau(IdGenerator.next("YC"));
        yeuCau.setDonHang(don);
        yeuCau.setLyDoChung(lyDo);
        yeuCau.setNgayTao(LocalDateTime.now());
        yeuCau.setTrangThai(TrangThaiDoiTra.CHO_DUYET);
        LoaiYeuCauDoiTra loai = r.loaiYeuCau() == null
                ? LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN : r.loaiYeuCau();

        if (loai == LoaiYeuCauDoiTra.DOI_HANG) {
            if (plan == null) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng sử dụng biểu mẫu đổi máy mới");
            yeuCau.setLyDoDoi(plan.reason()); yeuCau.setMaPhienBanDoi(plan.targetId());
            yeuCau.setGiaTriDoi(plan.oldNet()); yeuCau.setTienBu(plan.delta());
        }
        for (ChiTietDonHang c : mucYeuCau) {
            int soLuong = r.soLuong() == null ? c.getSoLuong() : r.soLuong();
            if (soLuong <= 0 || soLuong > c.getSoLuong()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng đổi trả không hợp lệ");
            }
            ChiTietDoiTra ct = new ChiTietDoiTra();
            ct.setMaChiTietDoiTra(IdGenerator.next("CTDT"));
            ct.setYeuCau(yeuCau);
            ct.setChiTietDonHang(c);
            ct.setSoLuong(soLuong);
            ct.setLoaiYeuCau(loai);
            ct.setLyDo(lyDo);
            ct.setHinhAnhMinhChung(r.hinhAnhMinhChung());
            yeuCau.getChiTiet().add(ct);
        }
        repo.save(yeuCau);
        thongBao.guiQuanTri("Có yêu cầu đổi trả mới cho đơn " + don.getMaDonHang());
        return dto(yeuCau);
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> cuaToi(String maKh) {
        return donRepo.findByKhachHangMaKhOrderByNgayMuaDesc(maKh).stream()
                .flatMap(d -> repo.findByDonHangMaDonHangOrderByNgayTaoDesc(d.getMaDonHang()).stream())
                .sorted(Comparator.comparing(YeuCauDoiTra::getNgayTao).reversed())
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<DoiTraDto> tatCa() {
        return repo.findAll().stream()
                .sorted(Comparator.comparing(YeuCauDoiTra::getNgayTao).reversed())
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public DoiTraDto chiTietAdmin(String ma) {
        return dto(repo.findById(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu")));
    }

    public DoiTraDto capNhat(String ma, TrangThaiDoiTra moi) {
        return capNhat(ma, moi, null);
    }

    public DoiTraDto capNhat(String ma, TrangThaiDoiTra moi, String ghiChuXuLy) {
        // 2B.7E: khoa + nap lai (KhoaYeuCauDoiTraRepository) -> hai Admin khong cung hoan tat
        YeuCauDoiTra yeuCau = repo.findByIdForUpdate(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        boolean hopLe = switch (yeuCau.getTrangThai()) {
            case CHO_DUYET -> moi == TrangThaiDoiTra.DA_DUYET || moi == TrangThaiDoiTra.TU_CHOI;
            case DA_DUYET -> moi == TrangThaiDoiTra.DANG_XU_LY || moi == TrangThaiDoiTra.HOAN_TAT || (isExchange(yeuCau) && moi == TrangThaiDoiTra.TU_CHOI);
            case DANG_XU_LY -> moi == TrangThaiDoiTra.HOAN_TAT || (isExchange(yeuCau) && moi == TrangThaiDoiTra.TU_CHOI);
            default -> false;
        };
        if (!hopLe) throw new AppException(HttpStatus.BAD_REQUEST, "Không thể chuyển trạng thái này");

        String ghiChu = ghiChuXuLy == null ? null : ghiChuXuLy.trim();
        if (ghiChu != null && ghiChu.length() > 500) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ghi chú tối đa 500 ký tự");
        }
        if (moi == TrangThaiDoiTra.TU_CHOI && (ghiChu == null || ghiChu.isEmpty())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối");
        }
        Map<String, Object> truoc = snapshot(yeuCau);
        if (isExchange(yeuCau) && moi == TrangThaiDoiTra.DA_DUYET) approveExchange(yeuCau);
        if (isExchange(yeuCau) && moi == TrangThaiDoiTra.TU_CHOI && yeuCau.getTrangThai() != TrangThaiDoiTra.CHO_DUYET) {
            if (yeuCau.isDaGiaoMayDoi() || yeuCau.getNgayThuChenhLech() != null || yeuCau.getChiTiet().stream().anyMatch(ChiTietDoiTra::isDaNhapKho))
                throw new AppException(HttpStatus.CONFLICT, "Đã xử lý hàng trả hoặc đã thu tiền; cần đối soát thủ công, không thể từ chối tự động");
            if (yeuCau.getMaPhienBanDoi() != null) {
                var target = phienBanRepo.findByIdForUpdate(yeuCau.getMaPhienBanDoi())
                        .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, "Không tìm thấy phiên bản đã giữ"));
                int quantity = yeuCau.getChiTiet().stream().mapToInt(ChiTietDoiTra::getSoLuong).sum();
                khoService.thayDoiTonDoiTra(target, quantity, yeuCau, SecurityUtil.currentUserId(), "Giải phóng máy đổi " + ma);
            }
        }
        if (isExchange(yeuCau) && moi == TrangThaiDoiTra.HOAN_TAT && yeuCau.getMaPhienBanDoi() != null && !yeuCau.isDaGiaoMayDoi())
            throw new AppException(HttpStatus.CONFLICT, "Chưa giao máy đổi");
        if (moi == TrangThaiDoiTra.HOAN_TAT) {
            boolean hasReturn = yeuCau.getChiTiet().stream()
                    .anyMatch(ct -> ct.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN);
            if (yeuCau.getChiTiet().stream().anyMatch(ct -> !ct.isDaNhapKho())) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Cần xác nhận nhận và nhập kho sản phẩm trước khi hoàn tất yêu cầu");
            }
            if (hasReturn && yeuCau.getNgayHoanTien() == null) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Cần ghi nhận giao dịch hoàn tiền trước khi hoàn tất yêu cầu");
            }
        }
        yeuCau.setTrangThai(moi);
        yeuCau.setNgayCapNhat(java.time.LocalDateTime.now());
        if (ghiChu != null && !ghiChu.isEmpty()) yeuCau.setGhiChuXuLy(ghiChu);
        thongBao.gui(yeuCau.getDonHang().getKhachHang().getMaKh(),
                "Yêu cầu đổi trả đơn " + yeuCau.getDonHang().getMaDonHang() + ": " + nhom10.phonenest.util.AfterSalesStatusLabel.label(moi.name()),
                ThongBaoService.linkYeuCau(yeuCau.getMaYeuCau()));
        nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS,
                LoaiDoiTuongAudit.RETURN, ma, truoc, snapshot(yeuCau));
        return dto(yeuCau);
    }

    @Transactional(readOnly = true)
    public String layGhiChuNoiBo(String ma) {
        return repo.findById(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                "Không tìm thấy yêu cầu")).getGhiChuNoiBo();
    }

    public void luuGhiChuNoiBo(String ma, String ghiChu) {
        if (ghiChu != null && ghiChu.length() > 1000) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ghi chú nội bộ tối đa 1000 ký tự");
        }
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        String next = ghiChu == null ? "" : ghiChu.trim();
        if (!Objects.equals(y.getGhiChuNoiBo(), next)) {
            y.setGhiChuNoiBo(next);
            nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS, LoaiDoiTuongAudit.RETURN, ma,
                    Map.of(), Map.of("suKien", "INTERNAL_NOTE"));
        }
    }

    private boolean isExchange(YeuCauDoiTra y) {
        return y.getChiTiet().stream().anyMatch(c -> c.getLoaiYeuCau() == LoaiYeuCauDoiTra.DOI_HANG);
    }
    public void inspectExchange(String ma) {
        var y = repo.findByIdForUpdate(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!isExchange(y) || y.getTrangThai() != TrangThaiDoiTra.CHO_DUYET || y.getMaPhienBanDoi() == null || y.isDaKiemTraDoi())
            throw new AppException(HttpStatus.CONFLICT, "Không thể xác nhận kiểm tra yêu cầu đổi này");
        y.setDaKiemTraDoi(true);
    }
    private void approveExchange(YeuCauDoiTra y) {
        if (!y.isDaKiemTraDoi() || y.getMaPhienBanDoi() == null || y.getChiTiet().size() != 1)
            throw new AppException(HttpStatus.CONFLICT, "Cần nhận và kiểm tra máy cũ trước khi duyệt; yêu cầu cũ chưa có phương án đổi cần tạo lại");
        var originalPayment = nhom10.phonenest.util.OrderPaymentPolicy.effective(
                thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc(y.getDonHang().getMaDonHang()));
        if (originalPayment == null || originalPayment.getTrangThai() != TrangThaiThanhToan.DA_THANH_TOAN
                || !nhom10.phonenest.util.OrderPaymentPolicy.matchesOrderAmount(originalPayment, y.getDonHang().getTongThanhToan()))
            throw new AppException(HttpStatus.CONFLICT, "Đơn gốc chưa được xác nhận thanh toán đầy đủ; cần đối soát trước khi duyệt đổi");
        var item = y.getChiTiet().get(0);
        var target = phienBanRepo.findByIdForUpdate(y.getMaPhienBanDoi()).filter(p -> exchangeAvailable(p, item.getSoLuong()))
                .orElseThrow(() -> new AppException(HttpStatus.CONFLICT, "Máy đổi không còn đủ hàng"));
        if (ExchangePolicy.same(y.getLyDoDoi()) && !item.getChiTietDonHang().getPhienBan().getMaPhienBan().equals(target.getMaPhienBan()))
            throw new AppException(HttpStatus.CONFLICT, "Đổi 1–1 phải đúng phiên bản đã mua");
        var delta = exchangeDifference(y.getLyDoDoi(), y.getGiaTriDoi(), target.getGiaBan().multiply(BigDecimal.valueOf(item.getSoLuong())));
        // Do not silently change a quote after customer submission. Reject and request a new quote instead.
        if (delta.compareTo(y.getTienBu()) != 0) throw new AppException(HttpStatus.CONFLICT, "Giá máy đổi đã thay đổi; cần báo khách và tạo yêu cầu mới");
        khoService.thayDoiTonDoiTra(target, -item.getSoLuong(), y, SecurityUtil.currentUserId(), "Giữ máy đổi " + y.getMaYeuCau());
    }
    public void recordExchangePayment(String ma, String reference) {
        var y = repo.findByIdForUpdate(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!isExchange(y) || !y.isDaKiemTraDoi() || y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY || y.getTienBu() == null || y.getTienBu().signum() <= 0 || y.getNgayThuChenhLech() != null)
            throw new AppException(HttpStatus.CONFLICT, "Chưa đủ điều kiện ghi nhận thu chênh lệch hoặc đã ghi nhận");
        if (reference == null || reference.isBlank() || reference.length() > 150) throw new AppException(HttpStatus.BAD_REQUEST, "Nhập mã chứng từ thu tiền thực tế (tối đa 150 ký tự)");
        y.setMaThuChenhLech(reference.trim()); y.setNgayThuChenhLech(LocalDateTime.now());
        thongBao.gui(y.getDonHang().getKhachHang().getMaKh(), "Đã ghi nhận thu chênh lệch đổi máy " + y.getTienBu().toPlainString() + " VND", ThongBaoService.linkYeuCau(ma));
    }
    public void handoverExchange(String ma) {
        var y = repo.findByIdForUpdate(ma).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (!isExchange(y) || y.getMaPhienBanDoi() == null || !y.isDaKiemTraDoi() || y.isDaGiaoMayDoi() || y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY || y.getTienBu() == null || (y.getTienBu().signum() > 0 && y.getNgayThuChenhLech() == null) || !y.getChiTiet().stream().allMatch(ChiTietDoiTra::isDaNhapKho))
            throw new AppException(HttpStatus.CONFLICT, "Cần hoàn tất kiểm tra, xử lý máy cũ và thu đủ chênh lệch trước khi ghi nhận giao máy đổi");
        y.setDaGiaoMayDoi(true); // replacement stock was already reserved at approval
    }
    public DoiTraDto xacNhanNhapKho(String ma, String adminId, boolean sellable) {
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (y.getTrangThai() != TrangThaiDoiTra.DA_DUYET && y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY) {
            throw new AppException(HttpStatus.CONFLICT, "Chỉ nhập kho cho yêu cầu đã duyệt và đang xử lý");
        }
        if (y.getChiTiet().isEmpty() || y.getChiTiet().stream().anyMatch(ChiTietDoiTra::isDaNhapKho)) {
            throw new AppException(HttpStatus.CONFLICT, "Yêu cầu đã được nhập kho hoặc không có sản phẩm");
        }
        if (isExchange(y) && (y.getMaPhienBanDoi() == null || !y.isDaKiemTraDoi() || y.getTienBu() == null
                || (y.getTienBu().signum() > 0 && y.getNgayThuChenhLech() == null)))
            throw new AppException(HttpStatus.CONFLICT, "Cần duyệt phương án và thu đủ chênh lệch (nếu có) trước khi xử lý hàng trả");
        // Only inspect structured header; customer free-text description must not override condition.
        String header = Objects.toString(y.getLyDoChung(), "").split("\\r?\\nMô tả chi tiết: ", 2)[0];
        boolean unsafe = header.lines().anyMatch(l -> Set.of("Lý do: Máy lỗi", "Lý do: Giao sai hàng", "Tình trạng máy: Có lỗi").contains(l.strip()));
        // Unknown legacy payloads and faulty/wrong items are not automatically resalable.
        boolean addStock = sellable && !unsafe && (header.startsWith("Lý do: ") && header.contains("Tình trạng máy: "));
        y.setHangTraBanDuoc(addStock);
        LocalDateTime now = LocalDateTime.now();
        for (ChiTietDoiTra ct : y.getChiTiet()) {
            PhienBanSanPham variant = ct.getChiTietDonHang().getPhienBan();
            if (addStock) khoService.thayDoiTonDoiTra(variant, ct.getSoLuong(), y, adminId,
                    "Nhận hàng đổi trả " + y.getMaYeuCau());
            ct.setDaNhapKho(true);
            ct.setNgayNhapKho(now);
        }
        y.setNgayCapNhat(now);
        nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS, LoaiDoiTuongAudit.RETURN, ma,
                Map.of(), Map.of("suKien", "RESTOCK", "hangTraBanDuoc", addStock));
        return dto(y);
    }

    public DoiTraDto ghiNhanHoanTien(
            String ma, DoiTraDto.XacNhanHoanTienRequest request, String adminId) {
        YeuCauDoiTra y = repo.findByIdForUpdate(ma).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu"));
        if (y.getTrangThai() != TrangThaiDoiTra.DANG_XU_LY) {
            throw new AppException(HttpStatus.CONFLICT, "Chỉ ghi nhận hoàn tiền khi yêu cầu đang xử lý");
        }
        if (y.getNgayHoanTien() != null) {
            throw new AppException(HttpStatus.CONFLICT, "Yêu cầu này đã được ghi nhận hoàn tiền");
        }
        if (y.getChiTiet().isEmpty() || y.getChiTiet().stream()
                .anyMatch(ct -> ct.getLoaiYeuCau() != LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Yêu cầu không phải trả hàng hoàn tiền");
        }
        if (y.getChiTiet().stream().anyMatch(ct -> !ct.isDaNhapKho())) {
            throw new AppException(HttpStatus.CONFLICT, "Xác nhận đã nhận và nhập kho sản phẩm trước khi ghi nhận hoàn tiền");
        }
        String method = request.hinhThuc().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MOMO", "VNPAY", "CHUYEN_KHOAN", "TIEN_MAT", "KHAC").contains(method)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Hình thức hoàn tiền không hợp lệ");
        }
        boolean vnPayDangXuLy = "DANG_HOAN_TIEN".equals(y.getTrangThaiHoanVnPay());
        if (vnPayDangXuLy && !"VNPAY".equals(method)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Yêu cầu hoàn VNPay đang được xử lý; hãy đối soát cùng mã yêu cầu trước khi chọn hình thức khác");
        }
        int phanTram = request.phanTram() == null ? 100 : request.phanTram();
        if (phanTram < 1 || phanTram > 100) throw new AppException(HttpStatus.BAD_REQUEST, "Tỷ lệ hoàn tiền phải từ 1 đến 100");
        BigDecimal ceiling = tinhTongHoanDuKien(y);
        BigDecimal requested = request.soTien();
        String adjustment = Objects.toString(request.lyDoDieuChinh(), "").trim();
        var payment = nhom10.phonenest.util.OrderPaymentPolicy.effective(
                thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc(y.getDonHang().getMaDonHang()));
        if (payment == null || payment.getPhuongThuc() == null) {
            throw new AppException(HttpStatus.CONFLICT, "Chưa có dữ liệu thanh toán; cần đối soát trước khi hoàn tiền");
        }
        if (payment.getPhuongThuc() == PhuongThucTt.COD) {
            if (!nhom10.phonenest.util.OrderPaymentPolicy.codPaid(payment, y.getDonHang().getTongThanhToan())) {
                throw new AppException(HttpStatus.CONFLICT, "Chưa xác nhận thu đủ tiền COD. Vui lòng đối soát tại Quản lý đơn hàng trước khi hoàn tiền");
            }
            if (!"CHUYEN_KHOAN".equals(method)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đơn COD phải hoàn qua chuyển khoản");
            }
        }
        if (!method.equals(nhom10.phonenest.util.ReturnRefundPolicy.adminMethod(payment.getPhuongThuc(), y.getLyDoChung())))
            throw new AppException(HttpStatus.BAD_REQUEST, "Phải hoàn tiền theo phương thức được phép của giao dịch gốc");
        if (vnPayDangXuLy) {
            // Gửi lại VNPay: giữ nguyên số tiền từng dòng đã gửi cùng mã yêu cầu hoàn để tránh lệch đối soát.
            if (y.getChiTiet().stream().anyMatch(ct -> ct.getSoTienHoan() == null))
                throw new AppException(HttpStatus.CONFLICT, "Thiếu số tiền của yêu cầu hoàn VNPay đang xử lý; cần đối soát thủ công");
        } else {
            tinhSoTienHoanDuKien(y);
            if (requested == null && phanTram < 100 && adjustment.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng ghi lý do điều chỉnh số tiền hoàn");
            if (requested != null) {
                if (requested.stripTrailingZeros().scale() > 0 || requested.signum() <= 0 || requested.compareTo(ceiling) > 0)
                    throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền hoàn phải là số nguyên dương, không vượt giá trị đủ điều kiện");
                if (requested.compareTo(ceiling) < 0 && adjustment.isBlank())
                    throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng ghi lý do điều chỉnh số tiền hoàn");
                // Allocate the exact total, never exceeding each line's refundable amount.
                BigDecimal remaining = requested;
                BigDecimal remainingCeiling = y.getChiTiet().stream().map(c -> c.getSoTienHoan().setScale(0, RoundingMode.FLOOR)).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (requested.compareTo(remainingCeiling) > 0) throw new AppException(HttpStatus.BAD_REQUEST, "Số tiền vượt mức hoàn nguyên đồng");
                for (ChiTietDoiTra ct : y.getChiTiet()) {
                    BigDecimal base = ct.getSoTienHoan().setScale(0, RoundingMode.FLOOR);
                    BigDecimal allocated = remainingCeiling.signum() == 0 ? BigDecimal.ZERO :
                            remaining.multiply(base).divide(remainingCeiling, 0, RoundingMode.FLOOR).min(base);
                    ct.setSoTienHoan(allocated);
                    remaining = remaining.subtract(allocated);
                    remainingCeiling = remainingCeiling.subtract(base);
                }
            } else {
                for (ChiTietDoiTra ct : y.getChiTiet()) {
                    ct.setSoTienHoan(ct.getSoTienHoan().multiply(BigDecimal.valueOf(phanTram)).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR));
                }
            }
        }
        BigDecimal soTienHoan = tongTienHoan(y);
        if (soTienHoan.signum() <= 0) throw new AppException(HttpStatus.CONFLICT, "Số tiền hoàn phải lớn hơn 0");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paid = request.thoiDiemHoan() == null ? now : request.thoiDiemHoan();
        if (paid.isAfter(now) || y.getChiTiet().stream().anyMatch(ct -> ct.getNgayNhapKho() != null && paid.isBefore(ct.getNgayNhapKho())))
            throw new AppException(HttpStatus.BAD_REQUEST, "Thời điểm hoàn phải từ lúc nhập kho đến hiện tại");
        Map<String, Object> truoc = snapshot(y);
        String maGiaoDich;
        LocalDateTime ngayHoan;
        if ("MOMO".equals(method)) {
            // Hoàn thật qua MoMo; requestId cố định theo mã yêu cầu nên gửi lại không bị hoàn trùng.
            maGiaoDich = momoPaymentService.refund(
                    y.getDonHang().getMaDonHang(), soTienHoan, y.getMaYeuCau());
            ngayHoan = now;
        } else if ("VNPAY".equals(method)) {
            VnPayPaymentService.ReturnRefundResult result = vnPayPaymentService.refundReturn(y, soTienHoan, adminId);
            y.setTrangThaiHoanVnPay(result.status());
            y.setThongBaoHoanVnPay(result.message());
            maGiaoDich = "DA_HOAN_TIEN".equals(result.status()) ? result.transactionId() : null;
            // VNPay có thể trả "đang xử lý": chưa ghi nhận ngày hoàn, giữ yêu cầu để đối soát/gửi lại.
            ngayHoan = maGiaoDich != null ? (y.getNgayHoanTien() != null ? y.getNgayHoanTien() : now) : null;
        } else {
            if (request.maGiaoDich() == null || request.maGiaoDich().isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập mã giao dịch/đối soát");
            }
            maGiaoDich = request.maGiaoDich().trim();
            ngayHoan = paid;
        }
        y.setHinhThucHoanTien(method);
        y.setMaGiaoDichHoanTien(maGiaoDich);
        y.setNgayHoanTien(ngayHoan);
        y.setNgayCapNhat(now);
        Map<String, Object> sau = snapshot(y);
        sau.put("suKien", ngayHoan != null ? "REFUND" : "REFUND_PENDING");
        sau.put("giaTriDuDieuKien", ceiling);
        sau.put("soTienHoan", soTienHoan);
        sau.put("lyDoDieuChinh", adjustment);
        sau.put("ngayHoanTien", ngayHoan == null ? null : ngayHoan.toString());
        nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_RETURN_STATUS,
                LoaiDoiTuongAudit.RETURN, ma, truoc, sau);
        return dto(y);
    }

    // Phân bổ giảm giá theo từng dòng hàng để tính đúng số tiền phải hoàn.
    private void tinhSoTienHoanDuKien(YeuCauDoiTra y) {
        DonHang don = y.getDonHang();
        BigDecimal tam = don.getTamTinh() == null ? BigDecimal.ZERO : don.getTamTinh();
        BigDecimal giam = don.getGiamGia() == null ? BigDecimal.ZERO : don.getGiamGia();
        for (ChiTietDoiTra ct : y.getChiTiet()) {
            if (ct.getLoaiYeuCau() != LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN) continue;
            BigDecimal giaTri = ct.getChiTietDonHang().getDonGia()
                    .multiply(BigDecimal.valueOf(ct.getSoLuong()));
            BigDecimal phanBo = tam.signum() == 0 ? BigDecimal.ZERO
                    : giam.multiply(giaTri).divide(tam, 0, RoundingMode.FLOOR);
            ct.setSoTienHoan(giaTri.subtract(phanBo).max(BigDecimal.ZERO));
        }
    }

    private BigDecimal tongTienHoan(YeuCauDoiTra y) {
        return y.getChiTiet().stream().map(ChiTietDoiTra::getSoTienHoan)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> snapshot(YeuCauDoiTra y) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("trangThai", y.getTrangThai().name());
        duLieu.put("phanHoi", y.getGhiChuXuLy());
        duLieu.put("hinhThucHoanTien", y.getHinhThucHoanTien());
        duLieu.put("maGiaoDichHoanTien", y.getMaGiaoDichHoanTien());
        duLieu.put("trangThaiHoanVnPay", y.getTrangThaiHoanVnPay());
        duLieu.put("maYeuCauHoanVnPay", y.getMaYeuCauHoanVnPay());
        return duLieu;
    }

    private DoiTraDto dto(YeuCauDoiTra y) {
        ChiTietDoiTra ct = y.getChiTiet().isEmpty() ? null : y.getChiTiet().get(0);
        String img = y.getChiTiet().stream().map(ChiTietDoiTra::getHinhAnhMinhChung)
                .filter(Objects::nonNull).findFirst().orElse(null);
        String tenSanPham = ct == null ? null : ct.getChiTietDonHang().getTenSanPham()
                + " - " + ct.getChiTietDonHang().getTenPhienBan();
        int soLuong = y.getChiTiet().stream().mapToInt(ChiTietDoiTra::getSoLuong).sum();
        List<DoiTraDto.ChiTietDto> items = y.getChiTiet().stream().map(item ->
                new DoiTraDto.ChiTietDto(item.getChiTietDonHang().getPhienBan().getMaPhienBan(),
                        item.getChiTietDonHang().getTenSanPham() + " - " + item.getChiTietDonHang().getTenPhienBan(),
                        item.getSoLuong(), item.getLoaiYeuCau(), item.isDaNhapKho(), item.getNgayNhapKho(),
                        item.getSoTienHoan())).toList();
        return new DoiTraDto(y.getMaYeuCau(), y.getDonHang().getMaDonHang(),
                y.getDonHang().getKhachHang().getHoTen(), y.getDonHang().getSoDienThoaiNhan(), y.getLyDoChung(), img,
                y.getNgayTao().toLocalDate(), y.getNgayCapNhat(), y.getTrangThai(),
                ct == null ? null : ct.getLoaiYeuCau(), tenSanPham,
                soLuong, y.getGhiChuXuLy(), items,
                y.getChiTiet().stream().anyMatch(item -> item.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)
                        ? (y.getNgayHoanTien() != null || "DANG_HOAN_TIEN".equals(y.getTrangThaiHoanVnPay())
                                ? tongTienHoan(y) : tinhTongHoanDuKien(y)) : null,
                y.getHinhThucHoanTien(), y.getMaGiaoDichHoanTien(), y.getNgayHoanTien(),
                !items.isEmpty() && items.stream().allMatch(DoiTraDto.ChiTietDto::daNhapKho), y.getNgayTao(),
                thanhToanRepo.findByDonHangMaDonHangOrderByNgayTaoDesc(y.getDonHang().getMaDonHang()).stream()
                        .findFirst().map(ThanhToan::getPhuongThuc).orElse(null),
                y.getTrangThaiHoanVnPay(), y.getMaYeuCauHoanVnPay(), y.getThongBaoHoanVnPay(),
                y.getMaPhienBanDoi() == null ? null : new DoiTraDto.ExchangeDto(y.getMaPhienBanDoi(), y.getLyDoDoi(), y.getGiaTriDoi(), y.getTienBu(), y.isDaKiemTraDoi(), y.getNgayThuChenhLech(), y.isDaGiaoMayDoi()), y.getHangTraBanDuoc());
    }

    private BigDecimal tinhTongHoanDuKien(YeuCauDoiTra y) {
        DonHang don = y.getDonHang();
        BigDecimal tam = don.getTamTinh() == null ? BigDecimal.ZERO : don.getTamTinh();
        BigDecimal giam = don.getGiamGia() == null ? BigDecimal.ZERO : don.getGiamGia();
        return y.getChiTiet().stream().filter(item -> item.getLoaiYeuCau() == LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN)
                .map(item -> {
                    BigDecimal giaTri = item.getChiTietDonHang().getDonGia().multiply(BigDecimal.valueOf(item.getSoLuong()));
                    BigDecimal phanBo = tam.signum() == 0 ? BigDecimal.ZERO : giam.multiply(giaTri).divide(tam, 0, RoundingMode.FLOOR);
                    return giaTri.subtract(phanBo).max(BigDecimal.ZERO);
                }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
