package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.service.DonHangService;
import nhom10.phonenest.service.ThanhToanService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class DonHangController {

    private final DonHangService donHangService;
    private final ThanhToanService thanhToanService;

    @PostMapping("/orders")
    public DonHangDto datHang(@Valid @RequestBody DonHangDto.Checkout request) {
        return donHangService.datHang(SecurityUtil.currentUserId(), request);
    }

    @GetMapping("/orders")
    public List<DonHangDto> lichSu() {
        return donHangService.lichSu(SecurityUtil.currentUserId());
    }

    @GetMapping("/orders/{ma}")
    public DonHangDto chiTiet(@PathVariable String ma) {
        return donHangService.chiTiet(SecurityUtil.currentUserId(), ma);
    }

    @PutMapping("/orders/{ma}/cancel")
    public DonHangDto huy(@PathVariable String ma, @Valid @RequestBody DonHangDto.HuyRequest req) {
        return donHangService.huy(SecurityUtil.currentUserId(), ma, req);
    }

    @PostMapping("/orders/{ma}/reorder")
    public GioHangDto muaLai(@PathVariable String ma) {
        return donHangService.muaLai(SecurityUtil.currentUserId(), ma);
    }

    @GetMapping("/checkout/preview")
    public CheckoutDto xemTruoc(@RequestParam String maDiaChi) {
        return thanhToanService.xemTruoc(SecurityUtil.currentUserId(), maDiaChi);
    }
}
