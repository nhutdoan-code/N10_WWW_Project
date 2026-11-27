package nhom10.phonenest.controller;

import nhom10.phonenest.dto.DoiTraDto;
import nhom10.phonenest.dto.AfterSalesInput;
import nhom10.phonenest.service.LuuTruAnhService;
import nhom10.phonenest.service.DoiTraService;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.util.SecurityUtil;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customer/returns")
@RequiredArgsConstructor
public class DoiTraController {

    private final DoiTraService doiTraService;
    private final LuuTruAnhService luuTruAnhService;

    @PostMapping
    public DoiTraDto tao(@RequestBody AfterSalesInput r) {
        String user = SecurityUtil.currentUserId();
        if (r.maDonHang() == null || r.maDonHang().isBlank() || (r.chiTiet() != null && (r.maPhienBan() == null || r.maPhienBan().isBlank())))
            throw new nhom10.phonenest.exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST, "Vui lòng chọn đơn hàng và sản phẩm");
        if (r.chiTiet() != null && r.loaiYeuCau() == null) throw new nhom10.phonenest.exception.AppException(org.springframework.http.HttpStatus.BAD_REQUEST, "Vui lòng chọn loại yêu cầu");
        var type = r.loaiYeuCau() == null ? nhom10.phonenest.enums.LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN : r.loaiYeuCau();
        return doiTraService.taoTuForm(user, r, type, luuTruAnhService).customerView();
    }

    @GetMapping("/exchange-options")
    public List<DoiTraService.ExchangeOption> exchangeOptions() { return doiTraService.exchangeOptions(); }

    @GetMapping
    public List<DoiTraDto> cuaToi() {
        return doiTraService.cuaToi(SecurityUtil.currentUserId()).stream().map(DoiTraDto::customerView).toList();
    }
}
