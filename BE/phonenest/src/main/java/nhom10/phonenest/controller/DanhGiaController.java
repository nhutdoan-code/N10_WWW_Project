package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.service.DanhGiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import nhom10.phonenest.service.LuuTruAnhService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer/reviews")
@RequiredArgsConstructor
public class DanhGiaController {

    private final DanhGiaService danhGiaService;
    private final LuuTruAnhService luuTruAnhService;

    @PostMapping("/images")
    public Map<String, String> uploadImage(@RequestParam MultipartFile file) {
        return Map.of("url", luuTruAnhService.luu(file));
    }

    @PostMapping
    public DanhGiaDto tao(@Valid @RequestBody DanhGiaDto.Request r) {
        return danhGiaService.tao(SecurityUtil.currentUserId(), r);
    }

    @GetMapping("/mine")
    public List<DanhGiaDto> cuaToi() {
        return danhGiaService.cuaToi(SecurityUtil.currentUserId());
    }

    @GetMapping("/order/{ma}")
    public List<DanhGiaDto.TrangThai> theoDonHang(@PathVariable String ma) {
        return danhGiaService.theoDonHang(SecurityUtil.currentUserId(), ma);
    }
}
