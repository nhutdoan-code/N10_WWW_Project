package nhom10.phonenest.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.AdminApprovalDto;
import nhom10.phonenest.dto.AdminUnblockDto;
import nhom10.phonenest.dto.LoaiSanPhamDto;
import nhom10.phonenest.dto.PheDuyetChiTietDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.service.LoaiSanPhamService;
import nhom10.phonenest.service.LuuTruAnhService;
import nhom10.phonenest.service.PheDuyetBanHangService;
import nhom10.phonenest.service.SanPhamService;
import nhom10.phonenest.service.UploadOrphanAuditService;
import nhom10.phonenest.service.ai.SemanticSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSanPhamController {

    private final SanPhamService sanPhamService;
    private final LoaiSanPhamService loaiSanPhamService;
    private final LuuTruAnhService luuTruAnhService;
    private final SemanticSearchService semanticSearchService;
    private final PheDuyetBanHangService pheDuyetBanHangService;
    private final UploadOrphanAuditService uploadOrphanAuditService;

    @PostMapping("/uploads")
    public Map<String, String> upload(@RequestParam MultipartFile file) {
        return Map.of("url", luuTruAnhService.luu(file));
    }

    /** 2B.7E: KIEM KE file upload mo coi — chi dry-run, KHONG xoa. */
    @GetMapping("/uploads/orphans")
    public UploadOrphanAuditService.BaoCao uploadOrphans(@RequestParam(required = false) Integer gioCho) {
        return uploadOrphanAuditService.kiemKe(gioCho);
    }

    @GetMapping("/products")
    public List<SanPhamDto> products(@RequestParam(required = false) String q) {
        return sanPhamService.list(q, null, null, null, null, true);
    }

    @GetMapping("/products/{ma}")
    public SanPhamDto product(@PathVariable String ma) {
        return sanPhamService.get(ma, true);
    }

    @GetMapping("/products/{ma}/impact")
    public Map<String, Object> productImpact(@PathVariable String ma) {
        return sanPhamService.getImpact(ma);
    }

    @PostMapping("/products/reindex")
    public Map<String, Object> reindex() {
        if (!semanticSearchService.isEmbeddingAvailable()) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể đồng bộ dữ liệu AI. Dịch vụ tư vấn thông minh hiện chưa sẵn sàng. Vui lòng thử lại sau."
            );
        }
        try {
            int count = semanticSearchService.reindexAll();
            return Map.of(
                    "thanhCong", true,
                    "soLuong", count,
                    "thongBao", "Đồng bộ dữ liệu AI thành công. Đã cập nhật " + count + " sản phẩm."
            );
        } catch (Exception e) {
            log.error("Lỗi khi đồng bộ dữ liệu AI: {}", e.getMessage(), e);
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể đồng bộ dữ liệu AI. Dịch vụ tư vấn thông minh hiện chưa sẵn sàng. Vui lòng thử lại sau."
            );
        }
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public SanPhamDto create(@Valid @RequestBody SanPhamDto d) {
        return sanPhamService.save(null, d);
    }

    @PutMapping("/products/{ma}")
    public SanPhamDto update(@PathVariable String ma, @Valid @RequestBody SanPhamDto d) {
        return sanPhamService.save(ma, d);
    }

    @PutMapping("/products/{ma}/archive")
    public SanPhamDto archiveProduct(@PathVariable String ma) {
        return sanPhamService.archive(ma);
    }

    @PutMapping("/products/{ma}/restore")
    public SanPhamDto restoreProduct(@PathVariable String ma) {
        return sanPhamService.restore(ma);
    }

    @GetMapping("/variants")
    public List<PheDuyetChiTietDto> variants(
            @RequestParam(required = false) TrangThaiPheDuyet trangThaiPheDuyet,
            @RequestParam(required = false) TrangThaiSanPham trangThai,
            @RequestParam(required = false) String maThuongHieu,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean chiKhongDuocBan
    ) {
        return pheDuyetBanHangService.danhSach(trangThaiPheDuyet, trangThai, maThuongHieu, q, chiKhongDuocBan);
    }

    @GetMapping("/variants/{maPhienBan}/approval-history")
    public List<Map<String, Object>> variantApprovalHistory(@PathVariable String maPhienBan) {
        return pheDuyetBanHangService.lichSu(maPhienBan);
    }

    @GetMapping("/variants/{maPhienBan}/approval")
    public PheDuyetChiTietDto getVariantApproval(@PathVariable String maPhienBan) {
        return pheDuyetBanHangService.chiTiet(maPhienBan);
    }

    @PutMapping("/variants/{maPhienBan}/approval")
    public PheDuyetChiTietDto updateVariantApproval(
            @PathVariable String maPhienBan,
            @Valid @RequestBody AdminApprovalDto request
    ) {
        return pheDuyetBanHangService.capNhatTrangThai(maPhienBan, request);
    }

    @PutMapping("/variants/{maPhienBan}/unblock")
    public PheDuyetChiTietDto unblockVariant(
            @PathVariable String maPhienBan,
            @Valid @RequestBody AdminUnblockDto request
    ) {
        return pheDuyetBanHangService.moKhoa(maPhienBan, request);
    }

    @DeleteMapping("/products/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String ma) {
        sanPhamService.delete(ma);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public LoaiSanPhamDto createCategory(@Valid @RequestBody LoaiSanPhamDto d) {
        return loaiSanPhamService.save(null, d);
    }

    @PutMapping("/categories/{ma}")
    public LoaiSanPhamDto updateCategory(@PathVariable String ma, @Valid @RequestBody LoaiSanPhamDto d) {
        return loaiSanPhamService.save(ma, d);
    }

    @DeleteMapping("/categories/{ma}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String ma) {
        loaiSanPhamService.delete(ma);
    }
}
