package nhom11.phonenest.service;

import nhom11.phonenest.dto.DonHangDto;
import nhom11.phonenest.dto.GioHangDto;
import nhom11.phonenest.entity.ChiTietDonHang;
import nhom11.phonenest.entity.DiaChi;
import nhom11.phonenest.entity.DonHang;
import nhom11.phonenest.entity.GioHang;
import nhom11.phonenest.entity.KhuyenMai;
import nhom11.phonenest.entity.SanPham;
import nhom11.phonenest.enums.TrangThaiDonHang;
import nhom11.phonenest.enums.TrangThaiSanPham;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.mapper.DonHangMapper;
import nhom11.phonenest.repository.DiaChiRepository;
import nhom11.phonenest.repository.DonHangRepository;
import nhom11.phonenest.repository.GioHangRepository;
import nhom11.phonenest.repository.KhachHangRepository;
import nhom11.phonenest.repository.KhuyenMaiRepository;
import nhom11.phonenest.repository.SanPhamRepository;
import nhom11.phonenest.repository.TaiKhoanRepository;
import nhom11.phonenest.util.HangThanhVien;
import nhom11.phonenest.util.IdGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DonHangService {

    private static final int DIEM_MOI_VND = 10000;

    private final DonHangRepository donHangRepository;
    private final GioHangRepository gioHangRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final SanPhamRepository sanPhamRepository;
    private final KhachHangRepository khachHangRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final DiaChiRepository diaChiRepository;
    private final DonHangMapper donHangMapper;
    private final GioHangService gioHangService;
    private final ThongBaoService thongBaoService;
    private final EmailService emailService;

    public DonHangDto datHang(String maKh, DonHangDto.Checkout req) {
        GioHang gio = gioHangRepository.findByMaKh(maKh)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng"));
        if (gio.getItems().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống");
        }
        DiaChi dc = diaChiRepository.findById(req.maDiaChi())
                .filter(x -> x.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Địa chỉ giao hàng không hợp lệ"));

        DonHang d = new DonHang();
        d.setMaDonHang(IdGenerator.next("DH"));
        d.setMaKh(maKh);
        d.setNgayMua(LocalDateTime.now());
        d.setNgayCapNhat(d.getNgayMua());
        d.setPhuongThucTt(req.phuongThucTt());
        d.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        d.setDiaChiGiaoHang(diaChiText(dc));
        if (req.maKm() != null && !req.maKm().isBlank()) {
            d.setKhuyenMai(khuyenMaiHopLe(req.maKm().trim()));
        }
        gio.getItems().forEach((ma, soLuong) -> {
            SanPham s = sanPhamRepository.findById(ma).orElseThrow();
            if (s.getTrangThai() != TrangThaiSanPham.CON_HANG || soLuong > s.getSoLuongTon()) {
                throw new AppException(HttpStatus.BAD_REQUEST, s.getTenMay() + " không đủ hàng, vui lòng cập nhật giỏ hàng");
            }
            d.getChiTiet().add(new ChiTietDonHang(ma, soLuong, s.getGiaBan()));
        });
        gio.getItems().clear();
        DonHang luu = donHangRepository.save(d);
        DonHangDto dto = donHangMapper.toDto(luu);

        thongBaoService.gui(maKh, "Đặt hàng thành công, đơn " + luu.getMaDonHang() + " đang chờ xác nhận");
        thongBaoService.guiQuanTri("Có đơn hàng mới " + luu.getMaDonHang());
        taiKhoanRepository.findById(maKh).ifPresent(tk -> emailService.gui(tk.getEmail(),
                "Xác nhận đơn hàng " + luu.getMaDonHang(),
                "Cảm ơn bạn đã đặt hàng tại PhoneNest. Tổng thanh toán: " + dto.tongTien().toBigInteger() + " VND. Giao đến: "
                        + luu.getDiaChiGiaoHang()));
        return dto;
    }

    @Transactional(readOnly = true)
    public DonHangDto.KhuyenMaiInfo xemKhuyenMai(String ma) {
        KhuyenMai k = khuyenMaiHopLe(ma.trim());
        return new DonHangDto.KhuyenMaiInfo(k.getMaKm(), k.getTenKm(), k.getPhanTramGiam(), k.getSoTienGiamToiDa());
    }

    @Transactional(readOnly = true)
    public List<DonHangDto> lichSu(String maKh) {
        return donHangRepository.findByMaKhOrderByNgayMuaDesc(maKh).stream().map(donHangMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DonHangDto chiTiet(String maKh, String ma) {
        return donHangMapper.toDto(donCuaToi(maKh, ma));
    }

    public DonHangDto huy(String maKh, String ma) {
        DonHang d = donCuaToi(maKh, ma);
        if (d.getTrangThai() != TrangThaiDonHang.CHO_XAC_NHAN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ được hủy đơn khi đang chờ xác nhận");
        }
        d.setTrangThai(TrangThaiDonHang.DA_HUY);
        d.setNgayCapNhat(LocalDateTime.now());
        thongBaoService.guiQuanTri("Khách hàng đã hủy đơn " + ma);
        return donHangMapper.toDto(d);
    }

    public GioHangDto muaLai(String maKh, String ma) {
        List<GioHangDto.Request> danhSach = donCuaToi(maKh, ma).getChiTiet().stream()
                .map(c -> new GioHangDto.Request(c.getMaSanPham(), c.getSoLuong()))
                .toList();
        return gioHangService.sync(maKh, danhSach);
    }

    @Transactional(readOnly = true)
    public List<DonHangDto> tatCa() {
        return donHangRepository.findAllByOrderByNgayMuaDesc().stream().map(donHangMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DonHangDto chiTietQuanTri(String ma) {
        return donHangMapper.toDto(tim(ma));
    }

    public DonHangDto capNhatTrangThai(String ma, TrangThaiDonHang moi) {
        DonHang d = tim(ma);
        TrangThaiDonHang cu = d.getTrangThai();
        if (cu == TrangThaiDonHang.DA_GIAO || cu == TrangThaiDonHang.DA_HUY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã kết thúc, không thể đổi trạng thái");
        }
        boolean chuaTruKho = cu.ordinal() < TrangThaiDonHang.DANG_GIAO.ordinal();
        if (chuaTruKho && (moi == TrangThaiDonHang.DANG_GIAO || moi == TrangThaiDonHang.DA_GIAO)) {
            doiTonKho(d, -1);
        } else if (cu == TrangThaiDonHang.DANG_GIAO && moi == TrangThaiDonHang.DA_HUY) {
            doiTonKho(d, 1);
        }
        d.setTrangThai(moi);
        d.setNgayCapNhat(LocalDateTime.now());
        DonHangDto dto = donHangMapper.toDto(d);
        if (moi == TrangThaiDonHang.DA_GIAO) {
            congDiem(d, dto);
        }
        thongBaoService.gui(d.getMaKh(), "Đơn hàng " + ma + " chuyển sang trạng thái: " + nhan(moi));
        return dto;
    }

    public DonHangDto suaDong(String ma, String maSanPham, int soLuong) {
        DonHang d = tim(ma);
        if (d.getTrangThai() != TrangThaiDonHang.CHO_XAC_NHAN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ được chỉnh sửa đơn khi đang chờ xác nhận");
        }
        ChiTietDonHang dong = d.getChiTiet().stream()
                .filter(c -> c.getMaSanPham().equals(maSanPham)).findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Sản phẩm không có trong đơn hàng"));
        if (soLuong <= 0) {
            if (d.getChiTiet().size() == 1) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng phải còn ít nhất một sản phẩm, hãy hủy đơn");
            }
            d.getChiTiet().remove(dong);
        } else {
            SanPham s = sanPhamRepository.findById(maSanPham).orElseThrow();
            if (soLuong > s.getSoLuongTon()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ còn " + s.getSoLuongTon() + " sản phẩm trong kho");
            }
            dong.setSoLuong(soLuong);
        }
        d.setNgayCapNhat(LocalDateTime.now());
        thongBaoService.gui(d.getMaKh(), "Cửa hàng đã điều chỉnh sản phẩm trong đơn " + ma);
        return donHangMapper.toDto(d);
    }

    private void congDiem(DonHang d, DonHangDto dto) {
        khachHangRepository.findById(d.getMaKh()).ifPresent(k -> {
            k.setDiemTichLuy(k.getDiemTichLuy() + dto.tongTien().intValue() / DIEM_MOI_VND);
            k.setHangThanhVien(HangThanhVien.of(k.getDiemTichLuy()));
        });
    }

    private void doiTonKho(DonHang d, int dau) {
        for (ChiTietDonHang c : d.getChiTiet()) {
            SanPham s = sanPhamRepository.findById(c.getMaSanPham()).orElseThrow();
            s.setSoLuongTon(Math.max(0, s.getSoLuongTon() + dau * c.getSoLuong()));
            if (s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH) {
                s.setTrangThai(s.getSoLuongTon() > 0 ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG);
            }
        }
    }

    private String nhan(TrangThaiDonHang t) {
        return switch (t) {
            case CHO_XAC_NHAN -> "Chờ xác nhận";
            case DANG_XU_LY -> "Đang xử lý";
            case DANG_GIAO -> "Đang giao";
            case DA_GIAO -> "Đã giao";
            case DA_HUY -> "Đã hủy";
        };
    }

    private String diaChiText(DiaChi dc) {
        String tinh = dc.getTinhThanhPho() == null || dc.getTinhThanhPho().isBlank() ? "" : ", " + dc.getTinhThanhPho();
        return dc.getTenNguoiNhan() + " - " + dc.getSoDienThoaiNhan() + " - " + dc.getDiaChiNhanHang() + tinh;
    }

    private DonHang tim(String ma) {
        return donHangRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private DonHang donCuaToi(String maKh, String ma) {
        return donHangRepository.findById(ma)
                .filter(d -> d.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private KhuyenMai khuyenMaiHopLe(String ma) {
        LocalDate hom = LocalDate.now();
        return khuyenMaiRepository.findById(ma)
                .filter(k -> !hom.isBefore(k.getNgayBatDau()) && !hom.isAfter(k.getNgayKetThuc()))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Mã khuyến mãi không hợp lệ hoặc đã hết hạn"));
    }
}
