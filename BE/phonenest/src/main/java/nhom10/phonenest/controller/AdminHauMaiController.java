package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.HauMaiDto;
import nhom10.phonenest.service.HauMaiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/after-sales")
@RequiredArgsConstructor
public class AdminHauMaiController {
    private final HauMaiService service;
    private final nhom10.phonenest.service.NhatKyHoatDongService audit;
    private final nhom10.phonenest.service.AfterSalesAdminWorkflow workflow;

    @GetMapping
    public List<HauMaiDto> danhSach() {
        return service.tatCa();
    }

    @GetMapping("/{ma}/internal-note")
    public java.util.Map<String, String> getInternalNote(@PathVariable String ma) {
        return java.util.Map.of("ghiChu", java.util.Objects.toString(service.layGhiChuNoiBo(ma), ""));
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{ma}/internal-note")
    public java.util.Map<String, String> setInternalNote(@PathVariable String ma,
            @RequestBody java.util.Map<String, String> request) {
        service.luuGhiChuNoiBo(ma, request.get("ghiChu"));
        audit.ghi(nhom10.phonenest.enums.HanhDongAudit.CHANGE_RETURN_STATUS, nhom10.phonenest.enums.LoaiDoiTuongAudit.RETURN, ma, java.util.Map.of(), java.util.Map.of("suKien", "INTERNAL_NOTE"));
        return java.util.Map.of("message", "Đã lưu ghi chú nội bộ");
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/{ma}/status")
    public HauMaiDto xuLy(@PathVariable String ma, @Valid @RequestBody HauMaiDto.XuLyRequest request) {
        if (request.trangThai() == nhom10.phonenest.enums.TrangThaiHauMai.HOAN_TAT) workflow.checkCompletion("after", ma);
        HauMaiDto result = service.xuLy(ma, request);
        audit.ghi(nhom10.phonenest.enums.HanhDongAudit.CHANGE_RETURN_STATUS, nhom10.phonenest.enums.LoaiDoiTuongAudit.RETURN, ma, java.util.Map.of(), java.util.Map.of("suKien", "STATUS", "trangThai", request.trangThai().name(), "phanHoi", java.util.Objects.toString(request.ghiChuXuLy(), "")));
        return result;
    }
}
