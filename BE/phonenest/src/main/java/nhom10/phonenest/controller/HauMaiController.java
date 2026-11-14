package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.service.HauMaiService;
import nhom10.phonenest.service.LuuTruAnhService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer/after-sales")
@RequiredArgsConstructor
public class HauMaiController {
    private final HauMaiService service;
    private final LuuTruAnhService luuTruAnhService;

    @PostMapping("/images")
    public Map<String, String> taiAnh(@RequestParam("file") MultipartFile file) {
        // Chung endpoint voi doi/tra va bao hanh, dung dich vu luu anh da co cua du an.
        return Map.of("url", luuTruAnhService.luu(file));
    }

    @PostMapping
    public HauMaiDto tao(@Valid @RequestBody HauMaiDto.Request request) {
        return service.tao(SecurityUtil.currentUserId(), request);
    }

    @GetMapping
    public List<HauMaiDto> danhSach() {
        return service.cuaToi(SecurityUtil.currentUserId());
    }

    @GetMapping("/{ma}")
    public HauMaiDto chiTiet(@PathVariable String ma) {
        return service.chiTietCuaToi(SecurityUtil.currentUserId(), ma);
    }
}
