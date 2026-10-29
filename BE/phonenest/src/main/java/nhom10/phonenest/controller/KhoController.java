package nhom10.phonenest.controller;

/*
 * @description
 * @author: LuuVanQuoc
 * @date: 10/29/2026
 * @version: 1.0
 */


import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.KhoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class KhoController {

    private final KhoService khoService;

    // Xem danh sách tồn kho
    // loc có thể là: het, sap-het hoặc bỏ trống
    @GetMapping
    public List<SanPhamDto> list(
            @RequestParam(required = false) String loc) {
        return khoService.list(loc);
    }

    // Nhập thêm hàng
    @PostMapping("/{ma}/nhap")
    public SanPhamDto nhapKho(
            @PathVariable String ma,
            @RequestParam int sl) {
        return khoService.nhapKho(ma, sl);
    }

    // Đặt lại số lượng tồn kho
    @PutMapping("/{ma}/ton")
    public SanPhamDto datTon(
            @PathVariable String ma,
            @RequestParam int sl) {
        return khoService.datTon(ma, sl);
    }
}
