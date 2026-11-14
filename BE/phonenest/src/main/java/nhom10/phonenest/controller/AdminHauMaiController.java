package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.service.HauMaiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/after-sales")
@RequiredArgsConstructor
public class AdminHauMaiController {
    private final HauMaiService service;

    @GetMapping
    public List<HauMaiDto> danhSach() {
        return service.tatCa();
    }

    @GetMapping("/{ma}/internal-note")
    public java.util.Map<String, String> getInternalNote(@PathVariable String ma) {
        return java.util.Map.of("ghiChu", java.util.Objects.toString(service.layGhiChuNoiBo(ma), ""));
    }

    @PutMapping("/{ma}/internal-note")
    public java.util.Map<String, String> setInternalNote(@PathVariable String ma,
            @RequestBody java.util.Map<String, String> request) {
        service.luuGhiChuNoiBo(ma, request.get("ghiChu"));
        return java.util.Map.of("message", "Đã lưu ghi chú nội bộ");
    }

    @PutMapping("/{ma}/status")
    public HauMaiDto xuLy(@PathVariable String ma, @Valid @RequestBody HauMaiDto.XuLyRequest request) {
        return service.xuLy(ma, request);
    }
}
