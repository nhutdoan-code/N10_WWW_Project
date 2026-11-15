package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.TaiKhoanDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.QuanTriVien;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.entity.Wishlist;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.repository.WishlistRepository;
import nhom10.phonenest.util.HangThanhVien;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class TaiKhoanService {

    private final TaiKhoanRepository taiKhoanRepository;
    private final KhachHangRepository khachHangRepository;
    private final QuanTriVienRepository quanTriVienRepository;
    private final DonHangRepository donHangRepository;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final GioHangRepository gioHangRepository;
    private final WishlistRepository wishlistRepository;
    private final PasswordEncoder passwordEncoder;

    public TaiKhoanDto taoKhachHang(TaiKhoanDto.Create request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String soDienThoai = request.soDienThoai().trim();
        if (taiKhoanRepository.existsByEmail(email)) {
            throw new AppException(HttpStatus.CONFLICT, "Email đã được sử dụng");
        }
        if (taiKhoanRepository.existsBySoDienThoai(soDienThoai)) {
            throw new AppException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        }

        String ma = IdGenerator.next("TK");
        TaiKhoan taiKhoan = new TaiKhoan();
        taiKhoan.setMaTk(ma);
        taiKhoan.setEmail(email);
        taiKhoan.setSoDienThoai(soDienThoai);
        taiKhoan.setMatKhau(passwordEncoder.encode(request.matKhau()));
        taiKhoan.setNgayTao(LocalDate.now());
        taiKhoan.setTrangThaiHoatDong(true);
        taiKhoan.setLoaiDangNhap("LOCAL");
        taiKhoanRepository.save(taiKhoan);

        KhachHang khachHang = new KhachHang();
        khachHang.setMaKh(ma);
        khachHang.setHoTen(request.hoTen().trim());
        khachHang.setNgaySinh(request.ngaySinh());
        khachHang.setDiemTichLuy(0);
        khachHang.setHangThanhVien(HangThanhVien.of(0));
        khachHangRepository.save(khachHang);

        GioHang gioHang = new GioHang();
        gioHang.setMaGioHang(IdGenerator.next("GH"));
        gioHang.setKhachHang(khachHang);
        gioHang.setNgayTao(LocalDateTime.now());
        gioHang.setNgayCapNhat(LocalDateTime.now());
        gioHangRepository.save(gioHang);

        Wishlist wishlist = new Wishlist();
        wishlist.setMaDanhSach(IdGenerator.next("WL"));
        wishlist.setMaKh(ma);
        wishlistRepository.save(wishlist);

        nhatKyHoatDongService.ghi(HanhDongAudit.CREATE_ACCOUNT, LoaiDoiTuongAudit.ACCOUNT,
                ma, null, snapshot(taiKhoan, khachHang));
        return toDto(taiKhoan);
    }

    @Transactional(readOnly = true)
    public List<TaiKhoanDto> list() {
        return taiKhoanRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<TaiKhoanDto> listAdminPage(String q, Pageable pageable) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<TaiKhoan> accountPage = taiKhoanRepository.searchAdminPage(pattern, pageable);
        List<String> ids = accountPage.getContent().stream().map(TaiKhoan::getMaTk).toList();
        Map<String, KhachHang> customers = new HashMap<>();
        for (KhachHang customer : khachHangRepository.findAllById(ids)) {
            customers.put(customer.getMaKh(), customer);
        }
        Map<String, QuanTriVien> admins = new HashMap<>();
        for (QuanTriVien admin : quanTriVienRepository.findAllById(ids)) {
            admins.put(admin.getMaQtv(), admin);
        }
        return accountPage.map(account -> toDto(account, admins.get(account.getMaTk()), customers.get(account.getMaTk())));
    }

    @Transactional(readOnly = true)
    public TaiKhoanDto get(String ma) {
        return toDto(tim(ma));
    }

    public TaiKhoanDto capNhat(String ma, TaiKhoanDto.Update u) {
        TaiKhoan tk = tim(ma);
        KhachHang kh = khachHangRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Chỉ được chỉnh sửa tài khoản khách hàng"));
        Map<String, Object> duLieuTruoc = snapshot(tk, kh);
        String sdt = u.soDienThoai();
        if (sdt != null && !sdt.isBlank() && !sdt.equals(tk.getSoDienThoai())) {
            if (taiKhoanRepository.existsBySoDienThoai(sdt)) {
                throw new AppException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
            }
            tk.setSoDienThoai(sdt);
        }
        kh.setHoTen(u.hoTen().trim());
        nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_ACCOUNT, LoaiDoiTuongAudit.ACCOUNT,
                ma, duLieuTruoc, snapshot(tk, kh));
        return toDto(tk);
    }

    public void xoa(String ma) {
        TaiKhoan tk = tim(ma);
        if (quanTriVienRepository.existsById(ma)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể xóa tài khoản quản trị");
        }
        if (donHangRepository.existsByKhachHangMaKh(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Tài khoản đã từng đặt hàng, hãy khóa tài khoản thay vì xóa");
        }
        Map<String, Object> duLieuTruoc = snapshot(tk, khachHangRepository.findById(ma).orElse(null));
        taiKhoanRepository.delete(tk);
        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_ACCOUNT, LoaiDoiTuongAudit.ACCOUNT,
                ma, duLieuTruoc, null);
    }

    private TaiKhoan tim(String ma) {
        return taiKhoanRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    }

    public TaiKhoanDto doiTrangThai(String ma) {
        TaiKhoan tk = taiKhoanRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (quanTriVienRepository.existsById(ma)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Không thể khóa tài khoản quản trị");
        }
        boolean trangThaiTruoc = tk.isTrangThaiHoatDong();
        Map<String, Object> duLieuTruoc = new LinkedHashMap<>();
        duLieuTruoc.put("trangThai", trangThaiTruoc);
        tk.setTrangThaiHoatDong(!trangThaiTruoc);
        Map<String, Object> duLieuSau = new LinkedHashMap<>();
        duLieuSau.put("trangThai", tk.isTrangThaiHoatDong());
        nhatKyHoatDongService.ghi(trangThaiTruoc ? HanhDongAudit.LOCK_ACCOUNT : HanhDongAudit.UNLOCK_ACCOUNT,
                LoaiDoiTuongAudit.ACCOUNT, ma, duLieuTruoc, duLieuSau);
        return toDto(tk);
    }

    /**
     * Snapshot an toan cho audit: chi field nghiep vu, khong bao gio gom matKhau/token.
     */
    private Map<String, Object> snapshot(TaiKhoan tk, KhachHang kh) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("hoTen", kh == null ? null : kh.getHoTen());
        duLieu.put("email", tk.getEmail());
        duLieu.put("soDienThoai", tk.getSoDienThoai());
        duLieu.put("trangThai", tk.isTrangThaiHoatDong());
        return duLieu;
    }

    public TaiKhoanDto toDto(TaiKhoan tk) {
        return toDto(tk,
                quanTriVienRepository.findById(tk.getMaTk()).orElse(null),
                khachHangRepository.findById(tk.getMaTk()).orElse(null));
    }

    private TaiKhoanDto toDto(TaiKhoan tk, QuanTriVien admin, KhachHang customer) {
        String hoTen = admin != null ? admin.getHoTen() : customer != null ? customer.getHoTen() : "";
        return new TaiKhoanDto(tk.getMaTk(), hoTen, tk.getEmail(), tk.getSoDienThoai(),
                admin != null ? Role.ADMIN : Role.CUSTOMER, tk.getNgayTao(), tk.isTrangThaiHoatDong());
    }
}
