package nhom10.phonenest.controller;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DanhGiaDto;
import nhom10.phonenest.dto.HoiDapDto;
import nhom10.phonenest.dto.LoaiSanPhamDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.dto.ThuongHieuDto;
import nhom10.phonenest.service.DanhGiaService;
import nhom10.phonenest.service.HoiDapService;
import nhom10.phonenest.service.LoaiSanPhamService;
import nhom10.phonenest.service.SanPhamService;
import nhom10.phonenest.service.ThuongHieuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SanPhamController {

    private final SanPhamService sanPhamService;
    private final LoaiSanPhamService loaiSanPhamService;
    private final ThuongHieuService thuongHieuService;
    private final DanhGiaService danhGiaService;
    private final HoiDapService hoiDapService;

    @GetMapping("/products")
    public List<SanPhamDto> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String maLoai,
                                 @RequestParam(required = false) BigDecimal min,
                                 @RequestParam(required = false) BigDecimal max,
                                 @RequestParam(required = false) String sort) {
        return sanPhamService.list(q, maLoai, min, max, sort, false);
    }

    @GetMapping("/products/{ma}")
    public SanPhamDto get(@PathVariable String ma) {
        return sanPhamService.get(ma, false);
    }

    @GetMapping("/products/{ma}/related")
    public List<SanPhamDto> lienQuan(@PathVariable String ma) {
        return sanPhamService.lienQuan(ma);
    }

    @GetMapping("/products/{ma}/reviews")
    public List<DanhGiaDto> danhGia(@PathVariable String ma) {
        return danhGiaService.theoSanPham(ma);
    }

    @GetMapping("/products/{ma}/questions")
    public List<HoiDapDto> hoiDap(@PathVariable String ma) {
        return hoiDapService.theoSanPham(ma);
    }

    @GetMapping("/categories")
    public List<LoaiSanPhamDto> categories() {
        return loaiSanPhamService.list();
    }

    @GetMapping("/brands")
    public List<ThuongHieuDto> brands() {
        return thuongHieuService.list();
    }
}
