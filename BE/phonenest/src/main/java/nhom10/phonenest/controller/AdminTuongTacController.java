package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.dto.HoiDapDto;
import nhom10.phonenest.enums.TrangThaiDoiTra;
import nhom10.phonenest.service.DanhGiaService;
import nhom10.phonenest.service.DoiTraService;
import nhom10.phonenest.service.HoiDapService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTuongTacController {

    private final DanhGiaService danhGiaService;
    private final HoiDapService hoiDapService;
    private final DoiTraService doiTraService;
    private final nhom10.phonenest.service.AfterSalesAdminWorkflow workflow;

    @GetMapping("/reviews")
    public List<DanhGiaDto> reviews() {
        return danhGiaService.tatCa();
    }

    @GetMapping("/reviews/page")
    public Page<DanhGiaDto> reviewsPage(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return danhGiaService.adminPage(q,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @DeleteMapping("/reviews/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaReview(@PathVariable String ma) {
        danhGiaService.xoa(ma);
    }

    @GetMapping("/questions")
    public List<HoiDapDto> questions() {
        return hoiDapService.tatCa();
    }

    @GetMapping("/questions/page")
    public Page<HoiDapDto> questionsPage(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return hoiDapService.adminPage(q,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @PutMapping("/questions/{ma}/answer")
    public HoiDapDto traLoi(@PathVariable String ma, @Valid @RequestBody HoiDapDto.TraLoi r) {
        return hoiDapService.traLoi(ma, r.noiDung());
    }

    @DeleteMapping("/questions/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoaQuestion(@PathVariable String ma) {
        hoiDapService.xoa(ma);
    }

    @GetMapping("/returns")
    public List<DoiTraDto> returns() {
        return doiTraService.tatCa();
    }

    @GetMapping("/returns/{ma}/internal-note")
    public java.util.Map<String, String> getInternalReturnNote(@PathVariable String ma) {
        return java.util.Map.of("ghiChu", java.util.Objects.toString(doiTraService.layGhiChuNoiBo(ma), ""));
    }

    @PutMapping("/returns/{ma}/internal-note")
    public java.util.Map<String, String> setInternalReturnNote(@PathVariable String ma,
            @RequestBody java.util.Map<String, String> request) {
        doiTraService.luuGhiChuNoiBo(ma, request.get("ghiChu"));
        return java.util.Map.of("message", "Đã lưu ghi chú nội bộ");
    }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/returns/{ma}/status")
    public DoiTraDto capNhatReturn(@PathVariable String ma,
                                   @RequestParam TrangThaiDoiTra trangThai,
                                   @RequestParam(required = false) String ghiChuXuLy) {
        if (trangThai == TrangThaiDoiTra.HOAN_TAT) workflow.checkCompletion("return", ma);
        return doiTraService.capNhat(ma, trangThai, ghiChuXuLy);
    }

    @PostMapping("/returns/{ma}/restock")
    public DoiTraDto xacNhanNhapKho(@PathVariable String ma, @RequestBody java.util.Map<String, String> body) {
        return workflow.restock(ma, SecurityUtil.currentUserId(), body.get("note"));
    }

    @PostMapping("/returns/{ma}/refund")
    public DoiTraDto ghiNhanHoanTien(@PathVariable String ma,
                                    @Valid @RequestBody DoiTraDto.XacNhanHoanTienRequest request) {
        return doiTraService.ghiNhanHoanTien(ma, request, SecurityUtil.currentUserId());
    }
}
