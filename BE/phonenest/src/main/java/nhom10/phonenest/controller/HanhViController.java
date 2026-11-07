package nhom10.phonenest.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.service.HanhViNguoiDungService;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class HanhViController {

    private final HanhViNguoiDungService hanhViNguoiDungService;

    @PostMapping("/events")
    public ResponseEntity<HanhViDto.EventResponse> trackEvent(
            @Valid @RequestBody HanhViDto.EventRequest request,
            HttpServletRequest httpRequest
    ) {
        String maKh = SecurityUtil.currentUserIdOrNull();
        String clientIp = extractClientIp(httpRequest);
        HanhViDto.EventResponse response = hanhViNguoiDungService.ghiNhan(request, maKh, clientIp);
        if (!response.success() && "RATE_LIMITED".equals(response.maHanhVi())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
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
