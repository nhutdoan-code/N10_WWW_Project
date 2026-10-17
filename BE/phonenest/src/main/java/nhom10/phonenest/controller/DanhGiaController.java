package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.service.DanhGiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/reviews")
@RequiredArgsConstructor
public class DanhGiaController {

    private final DanhGiaService danhGiaService;

    @PostMapping
    public DanhGiaDto tao(@Valid @RequestBody DanhGiaDto.Request r) {
        return danhGiaService.tao(SecurityUtil.currentUserId(), r);
    }

    @GetMapping("/mine")
    public List<DanhGiaDto> cuaToi() {
        return danhGiaService.cuaToi(SecurityUtil.currentUserId());
    }

    @GetMapping("/eligible/{ma}")
    public DanhGiaDto.Eligible kiemTra(@PathVariable String ma) {
        return danhGiaService.kiemTra(SecurityUtil.currentUserId(), ma);
    }
}
