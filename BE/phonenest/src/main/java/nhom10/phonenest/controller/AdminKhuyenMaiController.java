package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.KhuyenMaiDto;
import nhom10.phonenest.service.KhuyenMaiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
public class AdminKhuyenMaiController {

    private final KhuyenMaiService khuyenMaiService;

    @GetMapping
    public List<KhuyenMaiDto> list() {
        return khuyenMaiService.list();
    }

    @GetMapping("/{ma}/impact")
    public Map<String, Object> impact(@PathVariable String ma) {
        return khuyenMaiService.getImpact(ma);
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

    @PutMapping("/{ma}/archive")
    public KhuyenMaiDto archive(@PathVariable String ma) {
        return khuyenMaiService.archive(ma);
    }

    @PutMapping("/{ma}/restore")
    public KhuyenMaiDto restore(@PathVariable String ma) {
        return khuyenMaiService.restore(ma);
    }

    @DeleteMapping("/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable String ma) {
        khuyenMaiService.xoa(ma);
    }
}
