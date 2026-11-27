package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.dto.AfterSalesInput;
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
        try {
            return Map.of("url", luuTruAnhService.luuHauMai(file, SecurityUtil.currentUserId()));
        } catch (nhom10.phonenest.exception.AppException e) {
            throw new nhom10.phonenest.exception.AppException(e.getStatus(), "Ảnh " + java.util.Objects.toString(file.getOriginalFilename(), "") + ": " + e.getMessage());
        }
    }

    @PostMapping
    public HauMaiDto tao(@RequestBody AfterSalesInput request) {
        String user = SecurityUtil.currentUserId();
        if (request.loai() == null) throw new nhom10.phonenest.exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST, "Vui lòng chọn loại yêu cầu");
        String type = request.loai().name();
        return service.tao(user, new HauMaiDto.Request(request.maDonHang(), request.loai(), request.maPhienBan(), request.description(type), request.images(type, user, luuTruAnhService)));
    }

    @GetMapping("/policy")
    public Map<String, Integer> policy() {
        return Map.of("returnDays", nhom10.phonenest.util.AfterSalesPolicy.RETURN_DAYS,
                "warrantyMonths", nhom10.phonenest.util.AfterSalesPolicy.WARRANTY_MONTHS);
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
