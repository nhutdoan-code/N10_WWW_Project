package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.KhoDto;
import nhom10.phonenest.dto.PaymentStatusDto;
import nhom10.phonenest.dto.VnPayRefundStatusDto;
import nhom10.phonenest.dto.TaiKhoanDto;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.service.DonHangService;
import nhom10.phonenest.service.MomoPaymentService;
import nhom10.phonenest.service.TaiKhoanService;
import nhom10.phonenest.service.VnPayPaymentService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminQuanLyController {

    private final DonHangService donHangService;
    private final TaiKhoanService taiKhoanService;
    private final MomoPaymentService momoPaymentService;
    private final VnPayPaymentService vnPayPaymentService;

    @GetMapping("/orders")
    public List<DonHangDto> orders() {
        return donHangService.tatCa();
    }

    @PutMapping("/orders/{ma}/status")
    public DonHangDto capNhatTrangThai(@PathVariable String ma, @RequestParam TrangThaiDonHang trangThai) {
        if (trangThai == TrangThaiDonHang.DA_HUY) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vui lòng dùng chức năng hủy đơn để ghi nhận lý do");
        }
        return donHangService.capNhatTrangThai(ma, trangThai);
    }

    @PutMapping("/orders/{ma}/cancel")
    public DonHangDto huyDonQuanTri(@PathVariable String ma, @Valid @RequestBody DonHangDto.HuyRequest request) {
        return donHangService.huyQuanTri(ma, request);
    }
    @PutMapping("/orders/{ma}/confirm-cod-payment")
    public DonHangDto xacNhanThuTienCod(@PathVariable String ma) {
        return donHangService.xacNhanThuTienCod(ma);
    }

    @PutMapping("/orders/{ma}/mark-delivered")
    public DonHangDto danhDauDaGiao(@PathVariable String ma) {
        return donHangService.danhDauDaGiao(ma);
    }

    @GetMapping("/orders/{ma}")
    public DonHangDto order(@PathVariable String ma) {
        return donHangService.chiTietQuanTri(ma);
    }

    @GetMapping("/orders/{ma}/momo/refund-status")
    public PaymentStatusDto refundStatus(@PathVariable String ma) {
        return momoPaymentService.refundStatus(ma);
    }

    @PostMapping("/orders/{ma}/momo/refund")
    public PaymentStatusDto refundMomo(@PathVariable String ma) {
        return momoPaymentService.refundPendingPayment(ma);
    }

    @GetMapping("/orders/{ma}/vnpay/refund-status")
    public VnPayRefundStatusDto vnPayRefundStatus(@PathVariable String ma) {
        return vnPayPaymentService.refundStatus(ma);
    }

    @PostMapping("/orders/{ma}/vnpay/refund")
    public VnPayRefundStatusDto refundVnPay(@PathVariable String ma) {
        return vnPayPaymentService.refundPendingPayment(ma, SecurityUtil.currentUserId());
    }

    @PutMapping("/orders/{ma}/items/{maSanPham}")
    public DonHangDto suaDong(@PathVariable String ma, @PathVariable String maSanPham, @Valid @RequestBody KhoDto d) {
        return donHangService.suaDong(ma, maSanPham, d.soLuong());
    }

    @GetMapping("/accounts/{ma}")
    public TaiKhoanDto account(@PathVariable String ma) {
        return taiKhoanService.get(ma);
    }

    @PutMapping("/accounts/{ma}")
    public TaiKhoanDto capNhatAccount(@PathVariable String ma, @Valid @RequestBody TaiKhoanDto.Update u) {
        return taiKhoanService.capNhat(ma, u);
    }

    @DeleteMapping("/accounts/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaAccount(@PathVariable String ma) {
        taiKhoanService.xoa(ma);
    }

    @GetMapping("/accounts")
    public List<TaiKhoanDto> accounts() {
        return taiKhoanService.list();
    }

    @GetMapping("/accounts/page")
    public Page<TaiKhoanDto> accountsPage(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taiKhoanService.listAdminPage(q, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public TaiKhoanDto taoAccount(@Valid @RequestBody TaiKhoanDto.Create request) {
        return taiKhoanService.taoKhachHang(request);
    }

    @PutMapping("/accounts/{ma}/toggle-lock")
    public TaiKhoanDto toggleLock(@PathVariable String ma) {
        return taiKhoanService.doiTrangThai(ma);
    }
}
