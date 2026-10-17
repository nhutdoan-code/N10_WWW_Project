package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.service.GioHangService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/cart")
@RequiredArgsConstructor
public class GioHangController {

    private final GioHangService gioHangService;

    @GetMapping
    public GioHangDto get() {
        return gioHangService.get(SecurityUtil.currentUserId());
    }

    @PostMapping
    public GioHangDto add(@Valid @RequestBody GioHangDto.Request r) {
        return gioHangService.add(SecurityUtil.currentUserId(), r.maSanPham(), r.soLuong());
    }

    @PutMapping
    public GioHangDto update(@Valid @RequestBody GioHangDto.Request r) {
        return gioHangService.update(SecurityUtil.currentUserId(), r.maSanPham(), r.soLuong());
    }

    @PostMapping("/sync")
    public GioHangDto sync(@RequestBody List<GioHangDto.Request> danhSach) {
        return gioHangService.sync(SecurityUtil.currentUserId(), danhSach);
    }

    @DeleteMapping("/{ma}")
    public GioHangDto remove(@PathVariable String ma) {
        return gioHangService.remove(SecurityUtil.currentUserId(), ma);
    }

    @DeleteMapping
    public GioHangDto clear() {
        return gioHangService.clear(SecurityUtil.currentUserId());
    }
}
