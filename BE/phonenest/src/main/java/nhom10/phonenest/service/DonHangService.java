package nhom10.phonenest.service;

import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.entity.ChiTietDonHang;
import nhom10.phonenest.entity.DiaChi;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.HuyDonHang;
import nhom10.phonenest.entity.KhuyenMai;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.DiaChiRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.HuyDonHangRepository;
import nhom10.phonenest.repository.KhuyenMaiRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.util.HangThanhVien;
import nhom10.phonenest.util.IdGenerator;
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
    private static final String LY_DO_KHAC = "Lý do khác";
    private static final List<String> LY_DO_HUY = List.of(
            "Muốn thay đổi địa chỉ giao hàng",
            "Muốn nhập / thay đổi mã khuyến mãi",
            "Muốn thay đổi sản phẩm trong đơn (màu sắc, dung lượng, số lượng...)",
            "Thủ tục thanh toán quá rắc rối",
            "Tìm thấy giá tốt hơn ở nơi khác",
            "Đổi ý, không muốn mua nữa",
            LY_DO_KHAC);

    private final DonHangRepository donHangRepository;
    private final GioHangRepository gioHangRepository;
    private final KhuyenMaiRepository khuyenMaiRepository;
    private final HuyDonHangRepository huyDonHangRepository;
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
        doiTonKho(luu, -1);
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

    public DonHangDto huy(String maKh, String ma, DonHangDto.HuyRequest req) {
        DonHang d = donCuaToi(maKh, ma);
        TrangThaiDonHang tt = d.getTrangThai();
        if (tt == TrangThaiDonHang.DA_HUY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã được hủy trước đó");
        }
        if (tt != TrangThaiDonHang.CHO_XAC_NHAN && tt != TrangThaiDonHang.DANG_XU_LY) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Đơn hàng đã được bàn giao cho đơn vị vận chuyển, bạn không thể hủy.");
        }
        boolean khac = LY_DO_KHAC.equals(req.lyDo());
        if (!LY_DO_HUY.contains(req.lyDo())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Lý do hủy đơn không hợp lệ");
        }
        if (khac && (req.ghiChu() == null || req.ghiChu().isBlank())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do hủy khác");
        }
        doiTonKho(d, 1);
        d.setTrangThai(TrangThaiDonHang.DA_HUY);
        HuyDonHang h = new HuyDonHang();
        h.setMaDonHang(ma);
        h.setLyDo(req.lyDo());
        h.setGhiChu(khac ? req.ghiChu().trim() : null);
        h.setNgayHuy(LocalDateTime.now());
        huyDonHangRepository.save(h);
        d.setNgayCapNhat(LocalDateTime.now());
        thongBaoService.gui(maKh, "Bạn đã hủy đơn hàng " + ma + " thành công");
        thongBaoService.guiQuanTri("Khách hàng đã hủy đơn " + ma + ". Lý do: " + req.lyDo());
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
        if (moi == TrangThaiDonHang.DA_HUY) {
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
            capNhatTon(maSanPham, dong.getSoLuong());
            d.getChiTiet().remove(dong);
        } else {
            SanPham s = sanPhamRepository.findById(maSanPham).orElseThrow();
            int them = soLuong - dong.getSoLuong();
            if (them > s.getSoLuongTon()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ còn " + s.getSoLuongTon() + " sản phẩm trong kho");
            }
            capNhatTon(maSanPham, -them);
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
        d.getChiTiet().forEach(c -> capNhatTon(c.getMaSanPham(), dau * c.getSoLuong()));
    }

    private void capNhatTon(String maSanPham, int delta) {
        SanPham s = sanPhamRepository.findById(maSanPham).orElseThrow();
        s.setSoLuongTon(Math.max(0, s.getSoLuongTon() + delta));
        if (s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH) {
            s.setTrangThai(s.getSoLuongTon() > 0 ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG);
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
