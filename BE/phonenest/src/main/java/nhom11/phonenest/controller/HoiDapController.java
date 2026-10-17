package nhom11.phonenest.controller;

import nhom11.phonenest.dto.HoiDapDto;
import nhom11.phonenest.service.HoiDapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom11.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/questions")
@RequiredArgsConstructor
public class HoiDapController {

    private final HoiDapService hoiDapService;

    @PostMapping
    public HoiDapDto hoi(@Valid @RequestBody HoiDapDto.Hoi r) {
        return hoiDapService.hoi(SecurityUtil.currentUserId(), r);
    }

    @GetMapping
    public List<HoiDapDto> cuaToi() {
        return hoiDapService.cuaToi(SecurityUtil.currentUserId());
    }
}
