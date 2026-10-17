package nhom11.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import nhom11.phonenest.dto.DonHangDto;
import nhom11.phonenest.dto.KhoDto;
import nhom11.phonenest.dto.TaiKhoanDto;
import nhom11.phonenest.enums.TrangThaiDonHang;
import nhom11.phonenest.service.DonHangService;
import nhom11.phonenest.service.TaiKhoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminQuanLyController {

    private final DonHangService donHangService;
    private final TaiKhoanService taiKhoanService;

    @GetMapping("/orders")
    public List<DonHangDto> orders() {
        return donHangService.tatCa();
    }

    @PutMapping("/orders/{ma}/status")
    public DonHangDto capNhatTrangThai(@PathVariable String ma, @RequestParam TrangThaiDonHang trangThai) {
        return donHangService.capNhatTrangThai(ma, trangThai);
    }

    @GetMapping("/orders/{ma}")
    public DonHangDto order(@PathVariable String ma) {
        return donHangService.chiTietQuanTri(ma);
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

    @PutMapping("/accounts/{ma}/toggle-lock")
    public TaiKhoanDto toggleLock(@PathVariable String ma) {
        return taiKhoanService.doiTrangThai(ma);
    }
}
