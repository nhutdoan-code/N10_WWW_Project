package nhom10.phonenest.controller;

import nhom10.phonenest.dto.ThongBaoDto;
import nhom10.phonenest.service.ThongBaoService;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class ThongBaoController {

    private final ThongBaoService thongBaoService;

    @GetMapping
    public List<ThongBaoDto> list() {
        return thongBaoService.list(SecurityUtil.currentUserId());
    }

    @PutMapping("/{ma}/read")
    public void daDoc(@PathVariable String ma) {
        thongBaoService.daDoc(SecurityUtil.currentUserId(), ma);
    }

    @PutMapping("/read-all")
    public void docHet() {
        thongBaoService.docHet(SecurityUtil.currentUserId());
    }
}
