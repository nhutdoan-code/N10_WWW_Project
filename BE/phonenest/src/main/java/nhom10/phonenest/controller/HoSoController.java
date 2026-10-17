package nhom11.phonenest.controller;

import nhom11.phonenest.dto.HoSoDto;
import nhom11.phonenest.service.KhachHangService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom11.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/profile")
@RequiredArgsConstructor
public class HoSoController {

    private final KhachHangService khachHangService;

    @GetMapping
    public HoSoDto.Profile get() {
        return khachHangService.get(SecurityUtil.currentUserId());
    }

    @PutMapping
    public HoSoDto.Profile update(@Valid @RequestBody HoSoDto.Update u) {
        return khachHangService.update(SecurityUtil.currentUserId(), u);
    }

    @PutMapping("/password")
    public void doiMatKhau(@Valid @RequestBody HoSoDto.Password p) {
        khachHangService.doiMatKhau(SecurityUtil.currentUserId(), p);
    }
}
