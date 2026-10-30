package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.TaiKhoanDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.QuanTriVien;
import nhom10.phonenest.entity.TaiKhoan;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.QuanTriVienRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
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

    @Transactional(readOnly = true)
    public List<TaiKhoanDto> list() {
        return taiKhoanRepository.findAll().stream().map(this::toDto).toList();
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

    private TaiKhoanDto toDto(TaiKhoan tk) {
        var admin = quanTriVienRepository.findById(tk.getMaTk());
        String hoTen = admin.map(QuanTriVien::getHoTen)
                .orElseGet(() -> khachHangRepository.findById(tk.getMaTk()).map(KhachHang::getHoTen).orElse(""));
        return new TaiKhoanDto(tk.getMaTk(), hoTen, tk.getEmail(), tk.getSoDienThoai(),
                admin.isPresent() ? Role.ADMIN : Role.CUSTOMER, tk.getNgayTao(), tk.isTrangThaiHoatDong());
    }
}
