package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.NhatKyHoatDongChiTietDto;
import nhom10.phonenest.dto.NhatKyHoatDongDto;
import nhom10.phonenest.dto.TrangDto;
import nhom10.phonenest.service.NhatKyHoatDongService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminNhatKyHoatDongController {

    private final NhatKyHoatDongService nhatKyHoatDongService;

    @GetMapping
    public TrangDto<NhatKyHoatDongDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "thoiGian") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String keyword
    ) {
        return nhatKyHoatDongService.list(page, size, sort, direction, from, to, actor, action, entity, keyword);
    }

    @GetMapping("/{maNhatKy}")
    public NhatKyHoatDongChiTietDto get(@PathVariable String maNhatKy) {
        return nhatKyHoatDongService.get(maNhatKy);
    }
}
