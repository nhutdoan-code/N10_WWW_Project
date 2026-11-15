package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.ChiTietGioHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GioHangService {

    private final GioHangRepository gioHangRepository;
    private final PhienBanSanPhamRepository phienBanRepository;
    private final KhachHangRepository khachHangRepository;

    public GioHangDto get(String maKh) {
        return toDto(gioHang(maKh));
    }

    public GioHangDto add(String maKh, String ma, int sl) {
        GioHang g = gioHang(maKh);
        PhienBanSanPham p = resolve(ma);
        ChiTietGioHang c = find(g, p.getMaPhienBan());
        return set(g, p, (c == null ? 0 : c.getSoLuong()) + sl);
    }

    public GioHangDto update(String maKh, String ma, int sl) {
        GioHang g = gioHang(maKh);
        PhienBanSanPham p = resolve(ma);
        ChiTietGioHang c = find(g, p.getMaPhienBan());
        if (c == null) {
            return sl <= 0 ? toDto(g) : set(g, p, sl);
        }
        if (sl <= 0) {
            g.getChiTiet().remove(c);
        } else {
            validate(p, sl);
            c.setSoLuong(sl);
        }
        touch(g);
        return toDto(g);
    }

    public GioHangDto remove(String maKh, String ma) {
        GioHang g = gioHang(maKh);
        String id = resolve(ma).getMaPhienBan();
        g.getChiTiet().removeIf(c -> c.getPhienBan().getMaPhienBan().equals(id));
        touch(g);
        return toDto(g);
    }

    public GioHangDto clear(String maKh) {
        GioHang g = gioHang(maKh);
        g.getChiTiet().clear();
        touch(g);
        return toDto(g);
    }

    public GioHangDto sync(String maKh, List<GioHangDto.Request> ds) {
        GioHang g = gioHang(maKh);
        List<String> canhBao = new ArrayList<>();
        for (var r : ds) {
            // Phần tử trong List không được @Valid kiểm tra: bỏ qua số lượng không hợp lệ
            // (âm/0) để đồng bộ giỏ khách không thể làm giảm số lượng đã có.
            if (r == null || r.soLuong() < 1 || r.maPhienBan() == null || r.maPhienBan().isBlank()) {
                continue;
            }
            PhienBanSanPham p = phienBanRepository.findById(r.maPhienBan()).orElse(null);
            if (p == null) {
                canhBao.add("Một sản phẩm trong giỏ không còn tồn tại và đã được bỏ khỏi giỏ hàng");
                continue;
            }
            String ten = tenHienThi(p);
            ChiTietGioHang c = find(g, p.getMaPhienBan());
            int hienCo = c == null ? 0 : c.getSoLuong();
            try {
                kiemTraDuocBan(p);
            } catch (AppException e) {
                // Không mua được: không thêm dòng mới, giữ nguyên dòng đã có trong giỏ tài khoản.
                canhBao.add(ten + ": " + e.getMessage() + " nên không được thêm vào giỏ hàng");
                continue;
            }
            int muonCo = hienCo + r.soLuong();
            int n = Math.min(muonCo, p.getSoLuongTon());
            if (n < 1) {
                canhBao.add(ten + " đã hết hàng nên không được thêm vào giỏ hàng");
                continue;
            }
            if (n < muonCo) {
                canhBao.add(ten + " chỉ còn " + p.getSoLuongTon() + " sản phẩm, số lượng trong giỏ đã được điều chỉnh");
            }
            if (n <= hienCo) {
                continue;
            }
            if (c == null) {
                addLine(g, p, n);
            } else {
                c.setSoLuong(n);
            }
        }
        touch(g);
        GioHangDto dto = toDto(g);
        return new GioHangDto(dto.items(), dto.tongTien(), List.copyOf(canhBao));
    }

    private String tenHienThi(PhienBanSanPham p) {
        String ten = p.getSanPham() != null && p.getSanPham().getTenMay() != null ? p.getSanPham().getTenMay() : "Sản phẩm";
        return ten + (p.getMauSac() != null && !p.getMauSac().isBlank() ? " (" + p.getMauSac() + ")" : "");
    }

    private GioHangDto set(GioHang g, PhienBanSanPham p, int sl) {
        validate(p, sl);
        ChiTietGioHang c = find(g, p.getMaPhienBan());
        if (c == null) {
            addLine(g, p, sl);
        } else {
            c.setSoLuong(sl);
        }
        touch(g);
        return toDto(g);
    }

    private void addLine(GioHang g, PhienBanSanPham p, int sl) {
        ChiTietGioHang c = new ChiTietGioHang();
        c.setId(new ChiTietGioHang.Id(g.getMaGioHang(), p.getMaPhienBan()));
        c.setGioHang(g);
        c.setPhienBan(p);
        c.setSoLuong(sl);
        g.getChiTiet().add(c);
    }

    private void kiemTraDuocBan(PhienBanSanPham p) {
        if (p.getSanPham() == null || p.getSanPham().getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH
                || p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm hoặc phiên bản đã ngừng kinh doanh");
        }
        PheDuyetBanHangPolicy.kiemTraDuocBan(p, null);
    }

    private void validate(PhienBanSanPham p, int sl) {
        kiemTraDuocBan(p);
        if (sl < 1 || sl > p.getSoLuongTon()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng không hợp lệ (còn " + p.getSoLuongTon() + " sản phẩm)");
        }
    }

    private PhienBanSanPham resolve(String ma) {
        return phienBanRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Phiên bản sản phẩm không tồn tại"));
    }

    private ChiTietGioHang find(GioHang g, String id) {
        return g.getChiTiet().stream()
                .filter(c -> c.getPhienBan().getMaPhienBan().equals(id))
                .findFirst()
                .orElse(null);
    }

    private GioHang gioHang(String maKh) {
        return gioHangRepository.findByKhachHangMaKh(maKh).orElseGet(() -> {
            KhachHang kh = khachHangRepository.findById(maKh)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"));
            GioHang g = new GioHang();
            g.setMaGioHang(IdGenerator.next("GH"));
            g.setKhachHang(kh);
            g.setNgayTao(LocalDateTime.now());
            g.setNgayCapNhat(LocalDateTime.now());
            return gioHangRepository.save(g);
        });
    }

    private void touch(GioHang g) {
        g.setNgayCapNhat(LocalDateTime.now());
    }

    private SanPhamDto.PhienBan toPhienBanDto(PhienBanSanPham p) {
        return new SanPhamDto.PhienBan(
                p.getMaPhienBan(), p.getSku(), p.getRam(), p.getBoNho(), p.getMauSac(),
                // 2B.7D: dong gio cua bien the khong con duoc phep ban khong hien gia seed
                PheDuyetBanHangPolicy.giaCongBo(p), PheDuyetBanHangPolicy.giaNiemYetCongBo(p),
                p.getSoLuongTon(), p.getTrangThai(),
                p.getTrangThaiPheDuyet(), PheDuyetBanHangPolicy.duocPhepBan(p)
        );
    }

    private GioHangDto toDto(GioHang g) {
        List<GioHangDto.Item> items = g.getChiTiet().stream()
                .map(c -> new GioHangDto.Item(SanPhamMapper.toDto(c.getPhienBan().getSanPham()), toPhienBanDto(c.getPhienBan()), c.getSoLuong()))
                .toList();
        BigDecimal tong = g.getChiTiet().stream()
                .filter(c -> PheDuyetBanHangPolicy.duocPhepBan(c.getPhienBan()))
                .map(c -> c.getPhienBan().getGiaBan().multiply(BigDecimal.valueOf(c.getSoLuong())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GioHangDto(items, tong);
    }
}
