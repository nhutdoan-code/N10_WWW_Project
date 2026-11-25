package nhom10.phonenest.service;

import nhom10.phonenest.dto.ThongBaoDto;
import nhom10.phonenest.entity.ThongBao;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.ThongBaoRepository;
import nhom10.phonenest.util.IdGenerator;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ThongBaoService {

    private final ThongBaoRepository thongBaoRepository;
    private final QuanTriVienRepository quanTriVienRepository;

    public void gui(String maTk, String noiDung) {
        gui(maTk, noiDung, null);
    }

    /** Gửi thông báo kèm đường dẫn tới trang liên quan (xem {@link #linkDonHang} ...). */
    public void gui(String maTk, String noiDung, String duongDan) {
        ThongBao t = new ThongBao();
        t.setMaThongBao(IdGenerator.next("TB"));
        t.setMaTk(maTk);
        t.setNoiDung(noiDung);
        t.setDuongDan(duongDan);
        t.setDaDoc(false);
        t.setNgayTao(LocalDateTime.now());
        thongBaoRepository.save(t);
    }

    public void guiQuanTri(String noiDung) {
        quanTriVienRepository.findAll().forEach(a -> gui(a.getMaQtv(), noiDung));
    }

    public static String linkDonHang(String maDonHang) {
        return maDonHang == null ? null : "/orders/" + maDonHang;
    }

    /** Trang "Đổi trả & bảo hành" của khách; neo tới yêu cầu cụ thể nếu có. */
    public static String linkYeuCau(String maYeuCau) {
        return maYeuCau == null ? "/returns" : "/returns#" + maYeuCau;
    }

    public static String linkHoiDap(String maSanPham) {
        return maSanPham == null ? null : "/products/" + maSanPham + "#hoi-dap";
    }

    @Transactional(readOnly = true)
    public List<ThongBaoDto> list(String maTk) {
        return thongBaoRepository.findByMaTkOrderByNgayTaoDesc(maTk).stream()
                .map(t -> new ThongBaoDto(t.getMaThongBao(), t.getNoiDung(), t.isDaDoc(), t.getNgayTao(), t.getDuongDan()))
                .toList();
    }

    public void daDoc(String maTk, String ma) {
        thongBaoRepository.findById(ma).filter(t -> t.getMaTk().equals(maTk)).ifPresent(t -> t.setDaDoc(true));
    }

    public void docHet(String maTk) {
        thongBaoRepository.findByMaTkAndDaDocFalse(maTk).forEach(t -> t.setDaDoc(true));
    }
}
