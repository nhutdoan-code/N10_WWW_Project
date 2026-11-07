package nhom10.phonenest.controller;

import nhom10.phonenest.dto.KhoDto;
import nhom10.phonenest.dto.KhoDieuChinhDto;
import nhom10.phonenest.dto.KhoNhapHangLoatDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.KhoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminKhoController {

    private final KhoService khoService;

    @GetMapping
    public KhoService.KhoPageDto list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String loc,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return khoService.page(q, loc, page, size);
    }

    @GetMapping("/stats")
    public nhom10.phonenest.dto.KhoThongKeDto stats() {
        return khoService.thongKe();
    }

    @GetMapping("/history")
    public List<KhoService.BienDongKhoDto> history() {
        return khoService.lichSuTatCa();
    }

    @GetMapping("/{ma}/history")
    public List<KhoService.BienDongKhoDto> history(@PathVariable String ma) {
        return khoService.lichSu(ma);
    }

    @PostMapping("/{ma}/import")
    public SanPhamDto nhapKho(@PathVariable String ma, @Valid @RequestBody KhoDto d) {
        return khoService.nhapKho(ma, d.soLuong(), d.ghiChu(), SecurityUtil.currentUserId());
    }

    @PostMapping("/batch-import")
    public java.util.Map<String, Object> nhapKhoHangLoat(@Valid @RequestBody KhoNhapHangLoatDto d) {
        int count = khoService.nhapKhoHangLoat(d, SecurityUtil.currentUserId());
        return java.util.Map.of("message", "Nhập kho thành công", "count", count);
    }

    @PutMapping("/{ma}/adjust")
    public SanPhamDto dieuChinh(@PathVariable String ma, @Valid @RequestBody KhoDieuChinhDto d) {
        return khoService.dieuChinh(ma, d.soLuongThucTe(), d.lyDo(), SecurityUtil.currentUserId());
    }
}
