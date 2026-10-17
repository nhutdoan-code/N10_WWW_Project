package nhom10.phonenest.controller;

import nhom10.phonenest.dto.ThongKeDto;
import nhom10.phonenest.dto.TimKiemDto;
import nhom10.phonenest.service.ThongKeService;
import nhom10.phonenest.service.TimKiemService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminThongKeController {

    private final ThongKeService thongKeService;
    private final TimKiemService timKiemService;

    @GetMapping("/stats")
    public ThongKeDto thongKe(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den) {
        return thongKeService.thongKe(tu, den);
    }

    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> xuatCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tu,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate den) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao-cao-don-hang.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(thongKeService.xuatCsv(tu, den).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/search")
    public TimKiemDto timKiem(@RequestParam String q) {
        return timKiemService.tim(q);
    }
}
