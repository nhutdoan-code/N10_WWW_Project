package nhom10.phonenest.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.service.HanhViNguoiDungService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHanhViController {

    private final HanhViNguoiDungService hanhViNguoiDungService;

    @GetMapping("/behavior")
    public ResponseEntity<HanhViDto.BehaviorStatsResponse> getBehaviorStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(required = false) LoaiHanhViNguoiDung loaiHanhVi,
            @RequestParam(required = false) String maKh
    ) {
        HanhViDto.BehaviorStatsResponse stats = hanhViNguoiDungService.layThongKe(tuNgay, denNgay, loaiHanhVi, maKh);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/preferences/{maKh}")
    public ResponseEntity<HanhViDto.UserPreferenceDto> getUserPreferences(@PathVariable String maKh) {
        HanhViDto.UserPreferenceDto preferences = hanhViNguoiDungService.laySoThichNguoiDung(maKh);
        return ResponseEntity.ok(preferences);
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldEvents(
            @RequestParam(defaultValue = "90") int soNgayLuuTru,
            HttpServletRequest request
    ) {
        if (soNgayLuuTru < 7 || soNgayLuuTru > 3650) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số ngày lưu trữ không hợp lệ. Phải từ 7 đến 3650 ngày để đảm bảo an toàn dữ liệu.");
        }
        String adminId = SecurityUtil.currentUserIdOrNull();
        if (adminId == null || adminId.isBlank()) {
            adminId = "ADMIN";
        }
        String clientIp = extractClientIp(request);
        int deleted = hanhViNguoiDungService.xoaHanhViCu(soNgayLuuTru);
        log.warn("ADMIN [{}] (IP: {}) đã thực hiện dọn dẹp hành vi người dùng cũ hơn {} ngày. Số bản ghi đã xóa: {}",
                adminId, clientIp, soNgayLuuTru, deleted);
        return ResponseEntity.ok(Map.of(
                "thanhCong", true,
                "soBanGhiDaXoa", deleted,
                "soNgayLuuTru", soNgayLuuTru
        ));
    }

    private String extractClientIp(HttpServletRequest req) {
        if (req == null) return "UNKNOWN";
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String xRealIp = req.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String remoteAddr = req.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "UNKNOWN";
    }
}
