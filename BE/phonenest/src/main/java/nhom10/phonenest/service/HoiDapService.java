package nhom10.phonenest.service;

import nhom10.phonenest.dto.HoiDapDto;
import nhom10.phonenest.entity.HoiDap;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.HoiDapRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.util.IdGenerator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class HoiDapService {

    private final HoiDapRepository hoiDapRepository;
    private final SanPhamRepository sanPhamRepository;
    private final KhachHangRepository khachHangRepository;
    private final ThongBaoService thongBaoService;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    public HoiDapDto hoi(String maKh, HoiDapDto.Hoi r) {
        SanPham sp = sanPhamRepository.findById(r.maSanPham())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
        HoiDap h = new HoiDap();
        h.setMaCauHoi(IdGenerator.next("HD"));
        h.setMaKh(maKh);
        h.setMaSanPham(sp.getMaSanPham());
        h.setNoiDungCauHoi(r.noiDung().trim());
        h.setNgayHoi(LocalDate.now());
        hoiDapRepository.save(h);
        thongBaoService.guiQuanTri("Có câu hỏi mới về sản phẩm " + sp.getTenMay());
        return toDto(h);
    }

    @Transactional(readOnly = true)
    public List<HoiDapDto> theoSanPham(String maSanPham) {
        return hoiDapRepository.findByMaSanPhamOrderByNgayHoiDesc(maSanPham).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HoiDapDto> cuaToi(String maKh) {
        return hoiDapRepository.findByMaKhOrderByNgayHoiDesc(maKh).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HoiDapDto> tatCa() {
        return hoiDapRepository.findAllByOrderByNgayHoiDesc().stream().map(this::toDto).toList();
    }

    public HoiDapDto traLoi(String ma, String noiDung) {
        HoiDap h = tim(ma);
        Map<String, Object> duLieuTruoc = snapshot(h);
        h.setNoiDungTraLoi(noiDung.trim());
        h.setNgayTraLoi(LocalDate.now());
        thongBaoService.gui(h.getMaKh(), "Câu hỏi của bạn về sản phẩm đã được trả lời");
        nhatKyHoatDongService.ghi(HanhDongAudit.ANSWER_QUESTION, LoaiDoiTuongAudit.QUESTION,
                ma, duLieuTruoc, snapshot(h));
        return toDto(h);
    }

    public void xoa(String ma) {
        HoiDap h = tim(ma);
        Map<String, Object> duLieuTruoc = snapshot(h);
        hoiDapRepository.delete(h);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_QUESTION, LoaiDoiTuongAudit.QUESTION,
                ma, duLieuTruoc, null);
    }

    private Map<String, Object> snapshot(HoiDap h) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("maSanPham", h.getMaSanPham());
        duLieu.put("noiDungCauHoi", h.getNoiDungCauHoi());
        duLieu.put("noiDungTraLoi", h.getNoiDungTraLoi());
        return duLieu;
    }

    private HoiDap tim(String ma) {
        return hoiDapRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi"));
    }

    private HoiDapDto toDto(HoiDap h) {
        return new HoiDapDto(h.getMaCauHoi(), h.getMaSanPham(),
                sanPhamRepository.findById(h.getMaSanPham()).map(SanPham::getTenMay).orElse(""),
                khachHangRepository.findById(h.getMaKh()).map(KhachHang::getHoTen).orElse("Ẩn danh"),
                h.getNoiDungCauHoi(), h.getNoiDungTraLoi(), h.getNgayHoi(), h.getNgayTraLoi());
    }
}
