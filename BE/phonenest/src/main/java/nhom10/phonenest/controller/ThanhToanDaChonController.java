package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.service.ThanhToanDaChonService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Thanh toán các sản phẩm được chọn trong giỏ (thuộc /api/customer/** nên yêu cầu quyền CUSTOMER).
 * chon = danh sách mã phiên bản, ví dụ ?chon=PB1&chon=PB2
 */
@RestController
@RequestMapping("/api/customer")
public class ThanhToanDaChonController {

    private final ThanhToanDaChonService thanhToanDaChonService;

    public ThanhToanDaChonController(ThanhToanDaChonService thanhToanDaChonService) {
        this.thanhToanDaChonService = thanhToanDaChonService;
    }

    @GetMapping("/checkout/preview-selected")
    public CheckoutDto xemTruoc(@RequestParam String maDiaChi, @RequestParam List<String> chon) {
        return thanhToanDaChonService.xemTruoc(SecurityUtil.currentUserId(), maDiaChi, chon);
    }

    @PostMapping("/orders/selected")
    public DonHangDto datHang(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                              @RequestParam List<String> chon,
                              @Valid @RequestBody DonHangDto.Checkout request) {
        return thanhToanDaChonService.datHang(SecurityUtil.currentUserId(), idempotencyKey, request, chon);
    }
}
