package nhom11.phonenest.service;

import nhom11.phonenest.dto.HoiDapDto;
import nhom11.phonenest.entity.HoiDap;
import nhom11.phonenest.entity.KhachHang;
import nhom11.phonenest.entity.SanPham;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.repository.HoiDapRepository;
import nhom11.phonenest.repository.KhachHangRepository;
import nhom11.phonenest.repository.SanPhamRepository;
import nhom11.phonenest.util.IdGenerator;
import java.time.LocalDate;
import java.util.List;
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
        h.setNoiDungTraLoi(noiDung.trim());
        h.setNgayTraLoi(LocalDate.now());
        thongBaoService.gui(h.getMaKh(), "Câu hỏi của bạn về sản phẩm đã được trả lời");
        return toDto(h);
    }

    public void xoa(String ma) {
        hoiDapRepository.delete(tim(ma));
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
