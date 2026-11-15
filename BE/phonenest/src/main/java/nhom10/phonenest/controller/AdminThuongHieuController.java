package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.service.ThuongHieuService;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ThuongHieuDto create(@Valid @RequestBody ThuongHieuDto.Request request) {
        return thuongHieuService.save(null, request);
    }

    @PutMapping("/{ma}")
    public ThuongHieuDto update(@PathVariable String ma, @Valid @RequestBody ThuongHieuDto.Request request) {
        return thuongHieuService.save(ma, request);
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
