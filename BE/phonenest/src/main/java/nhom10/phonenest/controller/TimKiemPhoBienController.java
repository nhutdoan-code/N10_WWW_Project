package nhom10.phonenest.controller;

import nhom10.phonenest.service.TimKiemPhoBienService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /api/search/popular — từ khóa tìm nhiều và sản phẩm xem nhiều trong 30 ngày gần nhất.
 * Nằm dưới /api/search/** nên đã được SecurityConfig cho phép GET công khai.
 */
@RestController
@RequestMapping("/api/search")
public class TimKiemPhoBienController {

    private final TimKiemPhoBienService timKiemPhoBienService;

    public TimKiemPhoBienController(TimKiemPhoBienService timKiemPhoBienService) {
        this.timKiemPhoBienService = timKiemPhoBienService;
    }

    @GetMapping("/popular")
    public Map<String, Object> phoBien(@RequestParam(required = false) Integer limit) {
        return timKiemPhoBienService.tongHop(limit);
    }
}
