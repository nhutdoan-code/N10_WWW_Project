package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.service.GioHangService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/cart")
@RequiredArgsConstructor
public class GioHangController {

    private final GioHangService gioHangService;

    // Lấy giỏ hàng
    @GetMapping
    public GioHangDto get() {
        return gioHangService.get(SecurityUtil.currentUserId());
    }

    // Thêm phiên bản vào giỏ hàng
    @PostMapping
    public GioHangDto add(@Valid @RequestBody GioHangDto.Request r) {
        return gioHangService.add(
                SecurityUtil.currentUserId(),
                r.maPhienBan(),
                r.soLuong()
        );
    }

    // Cập nhật số lượng
    @PutMapping
    public GioHangDto update(@Valid @RequestBody GioHangDto.Request r) {
        return gioHangService.update(
                SecurityUtil.currentUserId(),
                r.maPhienBan(),
                r.soLuong()
        );
    }

    // Đồng bộ giỏ hàng
    @PostMapping("/sync")
    public GioHangDto sync(
            @Valid @RequestBody List<GioHangDto.Request> danhSach
    ) {
        return gioHangService.sync(
                SecurityUtil.currentUserId(),
                danhSach
        );
    }

    // Xóa một phiên bản
    @DeleteMapping("/{maPhienBan}")
    public GioHangDto remove(@PathVariable String maPhienBan) {
        return gioHangService.remove(SecurityUtil.currentUserId(), maPhienBan);
    }

    // Xóa toàn bộ giỏ hàng
    @DeleteMapping
    public GioHangDto clear() {
        return gioHangService.clear(SecurityUtil.currentUserId());
    }
}