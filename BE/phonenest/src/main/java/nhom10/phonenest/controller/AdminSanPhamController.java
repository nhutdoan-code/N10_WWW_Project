package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.LoaiSanPhamDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.service.LoaiSanPhamService;
import nhom10.phonenest.service.LuuTruAnhService;
import nhom10.phonenest.service.SanPhamService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSanPhamController {

    private final SanPhamService sanPhamService;
    private final LoaiSanPhamService loaiSanPhamService;
    private final LuuTruAnhService luuTruAnhService;

    @PostMapping("/uploads")
    public Map<String, String> upload(@RequestParam MultipartFile file) {
        return Map.of("url", luuTruAnhService.luu(file));
    }

    @GetMapping("/products")
    public List<SanPhamDto> products(@RequestParam(required = false) String q) {
        return sanPhamService.list(q, null, null, null, null, true);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public SanPhamDto create(@Valid @RequestBody SanPhamDto d) {
        return sanPhamService.save(null, d);
    }

    @PutMapping("/products/{ma}")
    public SanPhamDto update(@PathVariable String ma, @Valid @RequestBody SanPhamDto d) {
        return sanPhamService.save(ma, d);
    }

    @DeleteMapping("/products/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String ma) {
        sanPhamService.delete(ma);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public LoaiSanPhamDto createCategory(@Valid @RequestBody LoaiSanPhamDto d) {
        return loaiSanPhamService.save(null, d);
    }

    @PutMapping("/categories/{ma}")
    public LoaiSanPhamDto updateCategory(@PathVariable String ma, @Valid @RequestBody LoaiSanPhamDto d) {
        return loaiSanPhamService.save(ma, d);
    }

    @DeleteMapping("/categories/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String ma) {
        loaiSanPhamService.delete(ma);
    }
}
