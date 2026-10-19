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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTuongTacController {

    private final DanhGiaService danhGiaService;
    private final HoiDapService hoiDapService;
    private final DoiTraService doiTraService;

    @GetMapping("/reviews")
    public List<DanhGiaDto> reviews() {
        return danhGiaService.tatCa();
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

    @PutMapping("/returns/{ma}/status")
    public DoiTraDto capNhatReturn(@PathVariable String ma, @RequestParam TrangThaiDoiTra trangThai) {
        return doiTraService.capNhat(ma, trangThai);
    }
}
