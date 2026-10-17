package nhom10.phonenest.controller;

import nhom10.phonenest.dto.KhoDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.KhoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminKhoController {

    private final KhoService khoService;

    @GetMapping
    public List<SanPhamDto> list(@RequestParam(required = false) String loc) {
        return khoService.list(loc);
    }

    @PostMapping("/{ma}/import")
    public SanPhamDto nhapKho(@PathVariable String ma, @Valid @RequestBody KhoDto d) {
        return khoService.nhapKho(ma, d.soLuong());
    }

    @PutMapping("/{ma}")
    public SanPhamDto datTon(@PathVariable String ma, @Valid @RequestBody KhoDto d) {
        return khoService.datTon(ma, d.soLuong());
    }
}
