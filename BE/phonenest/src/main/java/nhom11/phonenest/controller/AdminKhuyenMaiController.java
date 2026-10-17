package nhom11.phonenest.controller;

import nhom11.phonenest.dto.KhuyenMaiDto;
import nhom11.phonenest.service.KhuyenMaiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import nhom11.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
public class AdminKhuyenMaiController {

    private final KhuyenMaiService khuyenMaiService;

    @GetMapping
    public List<KhuyenMaiDto> list() {
        return khuyenMaiService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KhuyenMaiDto tao(@Valid @RequestBody KhuyenMaiDto d) {
        return khuyenMaiService.tao(d);
    }

    @PutMapping("/{ma}")
    public KhuyenMaiDto sua(@PathVariable String ma, @Valid @RequestBody KhuyenMaiDto d) {
        return khuyenMaiService.sua(ma, d);
    }

    @DeleteMapping("/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable String ma) {
        khuyenMaiService.xoa(ma);
    }
}
