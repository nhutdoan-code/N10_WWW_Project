package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.service.ThuongHieuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminThuongHieuController {

    private final ThuongHieuService thuongHieuService;

    @GetMapping
    public List<ThuongHieuDto> list() {
        return thuongHieuService.listAll();
    }

    @GetMapping("/{ma}/impact")
    public Map<String, Object> impact(@PathVariable String ma) {
        return thuongHieuService.getImpact(ma);
    }

    @PutMapping("/{ma}/archive")
    public ThuongHieuDto archive(@PathVariable String ma) {
        return thuongHieuService.archive(ma);
    }

    @PutMapping("/{ma}/restore")
    public ThuongHieuDto restore(@PathVariable String ma) {
        return thuongHieuService.restore(ma);
    }
}
